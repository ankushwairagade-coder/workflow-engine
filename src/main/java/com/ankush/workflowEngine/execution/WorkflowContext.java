package com.ankush.workflowEngine.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowContext {

    private final Map<String, Object> data;

    private WorkflowContext(Map<String, Object> data) {
        this.data = data;
    }

    public static WorkflowContext fromMap(Map<String, Object> source) {
        Map<String, Object> initial = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        return new WorkflowContext(initial);
    }

    public void merge(Map<String, Object> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        data.putAll(additions);
    }

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(data);
    }
}
