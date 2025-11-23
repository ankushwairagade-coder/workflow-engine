package com.ankush.workflowEngine.support;

import com.ankush.workflowEngine.config.OllamaProperties;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class OllamaClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaClient.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_TOKENS = 500; // Limit response length for faster generation
    
    // Calculate timeout based on prompt size and max tokens
    private static final int TIMEOUT_PER_TOKEN_MS = 100; // 100ms per token estimate
    private static final int MIN_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;

    private final RestClient.Builder restClientBuilder;
    private final OllamaProperties properties;

    public OllamaClient(RestClient.Builder restClientBuilder, OllamaProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
    }

    public String generateText(String model, String prompt) {
        return generateText(model, prompt, DEFAULT_MAX_TOKENS);
    }

    public String generateText(String model, String prompt, int maxTokens) {
        // Calculate dynamic timeout based on prompt size and max tokens
        int estimatedTimeout = calculateTimeout(prompt.length(), maxTokens);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model != null ? model : properties.getDefaultModel());
        payload.put("prompt", prompt);
        payload.put("stream", false);
        payload.put("num_predict", maxTokens);
        payload.put("temperature", 0.7);

        try {
            LOGGER.debug("Calling Ollama API with model: {}, prompt length: {}, maxTokens: {}, timeout: {}s", 
                payload.get("model"), prompt.length(), maxTokens, estimatedTimeout);
            
            Map<?, ?> response = restClient(estimatedTimeout)
                    .post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        LOGGER.error("Ollama API returned error status: {}", res.getStatusCode());
                        throw new RestClientResponseException(
                            "Ollama API error: " + res.getStatusCode(),
                            res.getStatusCode().value(),
                            res.getStatusText(),
                            res.getHeaders(),
                            null,
                            null
                        );
                    })
                    .body(Map.class);
            
            if (response == null) {
                LOGGER.warn("Ollama returned null response");
                return "";
            }
            
            Object value = response.get("response");
            if (value == null) {
                LOGGER.warn("Ollama response missing 'response' field. Response keys: {}", response.keySet());
                return "";
            }
            
            String result = String.valueOf(value);
            LOGGER.debug("Ollama response received, length: {}", result.length());
            return result;
            
        } catch (RestClientException ex) {
            LOGGER.error("Failed to call Ollama: {}", ex.getMessage(), ex);
            
            // Check if it's a timeout
            if (ex.getCause() instanceof java.net.SocketTimeoutException) {
                LOGGER.error("Ollama request timed out after {} seconds. Consider reducing prompt size or max tokens.", estimatedTimeout);
                throw new NodeExecutionException("Ollama request timed out. The request may be too large or Ollama is slow. Try reducing the workflow size.", ex);
            }
            
            throw new NodeExecutionException("Ollama call failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            LOGGER.error("Unexpected error calling Ollama: {}", ex.getMessage(), ex);
            throw new NodeExecutionException("Unexpected error calling Ollama", ex);
        }
    }

    private int calculateTimeout(int promptLength, int maxTokens) {
        // Estimate: prompt processing + token generation
        // Rough estimate: 50ms per 100 chars + 100ms per token
        int estimatedMs = (promptLength / 100) * 50 + (maxTokens * TIMEOUT_PER_TOKEN_MS);
        int timeoutSeconds = Math.max(MIN_TIMEOUT_SECONDS, Math.min(MAX_TIMEOUT_SECONDS, (estimatedMs / 1000) + 10));
        return timeoutSeconds;
    }

    /**
     * Generates text with streaming support for real-time response display
     * Returns a Flux that emits text chunks as they are generated
     */
    public Flux<String> generateTextStream(String model, String prompt, int maxTokens) {
        return Flux.create(sink -> {
            try {
                String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "flowstack.ollama.base-url must be set");
                URL url = new URL(baseUrl + "/api/generate");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout((int) Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis());
                connection.setReadTimeout((int) Duration.ofSeconds(MAX_TIMEOUT_SECONDS).toMillis());
                
                // Build request payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("model", model != null ? model : properties.getDefaultModel());
                payload.put("prompt", prompt);
                payload.put("stream", true); // Enable streaming
                payload.put("num_predict", maxTokens);
                payload.put("temperature", 0.7);
                
                // Write request body
                String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                // Read streaming response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                        if (line.trim().isEmpty()) continue;
                        
                        try {
                            // Parse JSON line from Ollama stream
                            com.fasterxml.jackson.databind.JsonNode jsonNode = 
                                new com.fasterxml.jackson.databind.ObjectMapper().readTree(line);
                            
                            if (jsonNode.has("response")) {
                                String chunk = jsonNode.get("response").asText("");
                                if (!chunk.isEmpty()) {
                                    sink.next(chunk);
                                }
                            }
                            
                            // Check if done
                            if (jsonNode.has("done") && jsonNode.get("done").asBoolean(false)) {
                                break;
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Error parsing streaming chunk: {}", e.getMessage());
                        }
                    }
                }
                
                sink.complete();
                connection.disconnect();
                
            } catch (Exception ex) {
                LOGGER.error("Error in streaming: {}", ex.getMessage(), ex);
                sink.error(new NodeExecutionException("Streaming failed: " + ex.getMessage(), ex));
            }
        });
    }

    private RestClient restClient(int timeoutSeconds) {
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "flowstack.ollama.base-url must be set");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        
        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
