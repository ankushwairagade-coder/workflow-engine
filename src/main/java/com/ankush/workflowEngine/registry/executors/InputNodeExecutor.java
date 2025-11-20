package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InputNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputNodeExecutor.class);

    @Override
    public NodeType supportsType() {
        return NodeType.INPUT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> defaultsToMerge = null;
        
        // Extract defaults from config if present
        if (config != null && config.containsKey("defaults")) {
            Object defaultsObj = config.get("defaults");
            if (defaultsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> defaultsMap = (Map<String, Object>) defaultsObj;
                defaultsToMerge = defaultsMap;
            }
        } else if (config != null) {
            // If no "defaults" key, use entire config as defaults
            defaultsToMerge = config;
        }
        
        // Merge defaults into context (only adds missing keys, doesn't overwrite existing ones)
        if (defaultsToMerge != null && !defaultsToMerge.isEmpty()) {
            Map<String, Object> currentContext = context.context().snapshot();
            Map<String, Object> toMerge = new java.util.HashMap<>();
            
            // Only add keys that don't already exist in context
            for (Map.Entry<String, Object> entry : defaultsToMerge.entrySet()) {
                if (!currentContext.containsKey(entry.getKey())) {
                    toMerge.put(entry.getKey(), entry.getValue());
                }
            }
            
            if (!toMerge.isEmpty()) {
                context.context().merge(toMerge);
                LOGGER.debug("[FlowStack] Input node {} merged {} default values", 
                    context.node().getNodeKey(), toMerge.size());
            }
        }
        
        Map<String, Object> finalContext = context.context().snapshot();
        LOGGER.info("[FlowStack] Input node {} completed. Context has {} keys", 
            context.node().getNodeKey(), finalContext.size());
        
        return NodeExecutionResult.completed(finalContext, "input merged");
    }
}
