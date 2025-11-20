package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import com.ankush.workflowEngine.support.TemplateRenderer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpRequestNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestNodeExecutor.class);

    private final RestClient.Builder restClientBuilder;

    public HttpRequestNodeExecutor(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public NodeType supportsType() {
        return NodeType.HTTP;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> snapshot = context.context().snapshot();

        Object rawUrl = config.get("url");
        String urlTemplate = rawUrl != null ? rawUrl.toString() : null;
        if (urlTemplate == null || urlTemplate.isBlank()) {
            throw new NodeExecutionException("HTTP node requires a url in config");
        }
        String url = Objects.requireNonNull(TemplateRenderer.render(urlTemplate, snapshot), "Resolved url must not be null");
        if (url.isBlank()) {
            throw new NodeExecutionException("HTTP node resolved url is empty");
        }

        Object rawMethod = config.getOrDefault("method", "GET");
        String methodName = rawMethod != null ? rawMethod.toString() : null;
        final String normalizedMethod;
        if (methodName == null || methodName.isBlank()) {
            normalizedMethod = "GET";
        } else {
            normalizedMethod = methodName.trim().toUpperCase(Locale.ROOT);
        }
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(Objects.requireNonNull(normalizedMethod));
        } catch (IllegalArgumentException ex) {
            throw new NodeExecutionException("Unsupported HTTP method: " + normalizedMethod, ex);
        }

        Map<String, Object> headers = new LinkedHashMap<>();
        if (config.get("headers") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) {
                    headers.put(String.valueOf(key), value);
                }
            });
        }
        Object rawBody = config.get("body");
        String bodyTemplate = rawBody != null ? rawBody.toString() : null;
        String requestBody = bodyTemplate != null ? TemplateRenderer.render(bodyTemplate, snapshot) : null;

        try {
            var spec = restClientBuilder.build().method(httpMethod).uri(url);
            headers.forEach((key, value) -> {
                if (key == null) {
                    return;
                }
                String headerValue = value != null ? value.toString() : "";
                spec.header(key, headerValue);
            });
            if (requestBody != null && !requestBody.isBlank() && allowsBody(httpMethod)) {
                spec.body(requestBody);
            }
            ResponseEntity<String> response = spec.retrieve().toEntity(String.class);

            Map<String, Object> output = Map.of(
                    context.node().getNodeKey() + "::status", response.getStatusCode().value(),
                    context.node().getNodeKey() + "::body", response.getBody(),
                    context.node().getNodeKey() + "::url", url);
            context.context().merge(output);
            LOGGER.info("[FlowStack] HTTP node {} {} {}", context.node().getNodeKey(), httpMethod, url);
            return NodeExecutionResult.completed(output, "http request completed");
        } catch (RestClientException ex) {
            LOGGER.error("[FlowStack] HTTP node {} failed: {}", context.node().getNodeKey(), ex.getMessage());
            throw new NodeExecutionException("HTTP call failed", ex);
        }
    }

    private boolean allowsBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }
}
