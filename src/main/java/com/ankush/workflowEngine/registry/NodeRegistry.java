package com.ankush.workflowEngine.registry;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.support.ErrorMessageFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NodeRegistry {

    private final Map<NodeType, NodeExecutor> executors = new EnumMap<>(NodeType.class);

    public NodeRegistry(List<NodeExecutor> executorList) {
        executorList.forEach(this::register);
    }

    public NodeExecutor getExecutor(NodeType type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException(ErrorMessageFormatter.executorNotFound(type.name()));
        }
        return executor;
    }

    private void register(NodeExecutor executor) {
        executors.put(executor.supportsType(), executor);
    }
}
