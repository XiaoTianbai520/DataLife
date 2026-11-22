package com.digitallife.agentscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class OllamaEmbeddingModel implements EmbeddingModel {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String model;
    private final Duration recallTimeout;

    public OllamaEmbeddingModel(
            ObjectMapper mapper,
            @Value("${digitallife.memory.embedding-base-url:${digitallife.llm.base-url}}") String baseUrl,
            @Value("${digitallife.memory.embedding-model}") String model,
            @Value("${digitallife.memory.recall-timeout-seconds:5}") long recallTimeoutSeconds
    ) {
        this.mapper = mapper;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings");
        this.model = model;
        this.recallTimeout = Duration.ofSeconds(Math.max(1, recallTimeoutSeconds));
    }

    @Override
    public String vectorSpace() {
        return model;
    }

    @Override
    public float[] embedQuery(String text) {
        return embed("Instruct: Retrieve personal memories relevant to this conversation or observation.\nQuery: " + text,
                recallTimeout);
    }

    @Override
    public float[] embed(String text) {
        return embed(text, Duration.ofSeconds(35));
    }

    private float[] embed(String text, Duration timeout) {
        try {
            String body = mapper.writeValueAsString(Map.of("model", model, "input", text));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Embedding 端点 HTTP " + response.statusCode());
            }
            JsonNode vector = mapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("Embedding 响应缺少向量");
            }
            float[] result = new float[vector.size()];
            for (int index = 0; index < result.length; index++) {
                JsonNode value = vector.get(index);
                result[index] = value.floatValue();
                if (!value.isNumber() || !Float.isFinite(result[index])) {
                    throw new IllegalStateException("Embedding 响应包含无效元素");
                }
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding 请求已中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Embedding 端点无法访问", exception);
        }
    }
}
