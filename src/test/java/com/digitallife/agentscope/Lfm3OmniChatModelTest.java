package com.digitallife.agentscope;

import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Lfm3OmniChatModelTest {

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
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test", "test", false);
            var result = model.generateSingleStep(
                    List.of(Msg.builder().role(MsgRole.USER).textContent("测试").build()), List.of(), true);
            assertEquals(ChatModel.Outcome.INVALID_OUTPUT, result.outcome());
            assertTrue(result.speakCall().isEmpty());
        } finally {
            server.stop(0);
        }
    }
}
