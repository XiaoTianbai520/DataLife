package com.digitallife.agentscope;

import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Lfm3OmniChatModelTest {

    @Test
    void userTurnWaitsBeyondHeartbeatBudgetAndPreservesModelReasoning() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] response = """
                {"id":"test","choices":[{"index":0,"finish_reason":"stop","message":{
                  "role":"assistant","content":"","reasoning_content":"合成推理内容。"
                }}]}
                """.getBytes(StandardCharsets.UTF_8);
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                requestReceived.countDown();
                if (releaseResponse.await(5, TimeUnit.SECONDS)) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        Lfm3OmniChatModel model = new Lfm3OmniChatModel(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test", "test", false, 10, 1);
        try {
            var result = executor.submit(() -> model.generateSingleStep(
                    List.of(Msg.builder().role(MsgRole.USER).textContent("测试").build()), List.of(), true));
            assertTrue(requestReceived.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> result.get(1200, TimeUnit.MILLISECONDS));
            releaseResponse.countDown();
            var output = result.get(5, TimeUnit.SECONDS);
            assertEquals(ChatModel.Outcome.COMPLETED, output.outcome());
            assertEquals("", output.innerThought());
            assertEquals("合成推理内容。", output.reasoning());
            assertTrue(output.speakCall().isEmpty());
        } finally {
            releaseResponse.countDown();
            executor.shutdownNow();
            model.close();
            server.stop(0);
        }
    }

    @Test
    void malformedToolArgumentsAreSilentInsteadOfAnOutage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] response = """
                {"id":"test","choices":[{"index":0,"finish_reason":"tool_calls","message":{
                  "role":"assistant","content":"先想一想。",
                  "tool_calls":[{"id":"broken","type":"function","function":{"name":"speak","arguments":"{"}}]
                }}]}
                """.getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            Lfm3OmniChatModel model = new Lfm3OmniChatModel(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test", "test", false, 120, 30);
            var result = model.generateSingleStep(
                    List.of(Msg.builder().role(MsgRole.USER).textContent("测试").build()), List.of(), true);
            assertEquals(ChatModel.Outcome.INVALID_OUTPUT, result.outcome());
            assertTrue(result.speakCall().isEmpty());
        } finally {
            server.stop(0);
        }
    }
}
