package com.ankush.workflowEngine.support;

import com.ankush.workflowEngine.config.OllamaProperties;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OllamaClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient.Builder restClientBuilder;
    private final OllamaProperties properties;

    public OllamaClient(RestClient.Builder restClientBuilder, OllamaProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
    }

    public String generateText(String model, String prompt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model != null ? model : properties.getDefaultModel());
        payload.put("prompt", prompt);
        payload.put("stream", false);

        try {
            Map<?, ?> response = restClient()
                    .post()
                    .uri("/api/generate")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return "";
            }
            Object value = response.containsKey("response") ? response.get("response") : "";
            return value == null ? "" : String.valueOf(value);
        } catch (RestClientException ex) {
            LOGGER.warn("Failed to call Ollama: {}", ex.getMessage());
            throw new NodeExecutionException("Ollama call failed", ex);
        }
    }

    private RestClient restClient() {
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "flowstack.ollama.base-url must be set");
        return restClientBuilder.baseUrl(baseUrl).build();
    }
}
