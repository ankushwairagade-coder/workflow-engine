package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import com.ankush.workflowEngine.support.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * IF/ELSE node executor that evaluates a condition and outputs true/false
 * The workflow executor uses this result to decide which edge to follow
 */
@Component
public class IfElseNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(IfElseNodeExecutor.class);

    @Override
    public NodeType supportsType() {
        return NodeType.IF_ELSE;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> snapshot = context.context().snapshot();

        // Get condition expression from config
        Object rawCondition = config.get("condition");
        if (rawCondition == null) {
            throw new NodeExecutionException("IF_ELSE node requires a 'condition' field in config");
        }

        String conditionTemplate = rawCondition.toString();
        if (conditionTemplate.isBlank()) {
            throw new NodeExecutionException("IF_ELSE node condition cannot be empty");
        }

        // Render the condition template with context variables
        String renderedCondition = TemplateRenderer.render(conditionTemplate, snapshot);
        
        // Check if template variables were not resolved (still contain {{}})
        if (renderedCondition.contains("{{") && renderedCondition.contains("}}")) {
            LOGGER.warn("[FlowStack] IF_ELSE node {} has unresolved template variables in condition: {}", 
                context.node().getNodeKey(), renderedCondition);
            throw new NodeExecutionException(
                "Condition contains unresolved variables. Available context keys: " + 
                snapshot.keySet() + ". Condition: " + conditionTemplate);
        }
        
        // Evaluate the condition
        boolean result = evaluateCondition(renderedCondition, snapshot);

        // Output the result
        String nodeKey = context.node().getNodeKey();
        Map<String, Object> output = Map.of(
            nodeKey + "::result", result,
            nodeKey + "::condition", renderedCondition
        );

        LOGGER.info("[FlowStack] IF_ELSE node {} evaluated condition: {} = {}", 
            nodeKey, renderedCondition, result);

        return NodeExecutionResult.completed(output, 
            String.format("Condition evaluated to %s", result));
    }

    /**
     * Evaluates a condition expression
     * Supports simple boolean expressions like:
     * - Variable comparisons: "{{variable}} == 'value'"
     * - Boolean variables: "{{isActive}}"
     * - Numeric comparisons: "{{count}} > 10"
     */
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank()) {
            throw new NodeExecutionException("Condition cannot be empty");
        }

        // Trim whitespace
        condition = condition.trim();

        // Handle direct boolean values
        if ("true".equalsIgnoreCase(condition) || "false".equalsIgnoreCase(condition)) {
            return Boolean.parseBoolean(condition);
        }

        // Handle boolean variables from context
        if (condition.startsWith("{{") && condition.endsWith("}}")) {
            String varName = condition.substring(2, condition.length() - 2).trim();
            Object value = context.get(varName);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value != null) {
                // Truthy check: non-null, non-empty strings, non-zero numbers
                return !value.toString().isEmpty() && 
                       !value.toString().equals("0") && 
                       !value.toString().equalsIgnoreCase("false");
            }
            return false;
        }

        // Handle comparison operators
        if (condition.contains("==")) {
            return evaluateEquals(condition, context);
        } else if (condition.contains("!=")) {
            return !evaluateEquals(condition.replace("!=", "=="), context);
        } else if (condition.contains(">")) {
            return evaluateComparison(condition, ">", context);
        } else if (condition.contains("<")) {
            return evaluateComparison(condition, "<", context);
        } else if (condition.contains(">=")) {
            return evaluateComparison(condition, ">=", context);
        } else if (condition.contains("<=")) {
            return evaluateComparison(condition, "<=", context);
        }

        // Try to evaluate as a boolean expression using Spring Expression Language
        // For now, fallback to simple evaluation
        try {
            // Check if it's a context variable
            Object value = context.get(condition);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            
            // Try parsing as boolean
            return Boolean.parseBoolean(condition);
        } catch (Exception ex) {
            throw new NodeExecutionException(
                "Unable to evaluate condition: " + condition + ". " +
                "Supported formats: boolean values, {{variable}}, or comparisons (==, !=, >, <, >=, <=)",
                ex);
        }
    }

    private boolean evaluateEquals(String condition, Map<String, Object> context) {
        String[] parts = condition.split("==", 2);
        if (parts.length != 2) {
            throw new NodeExecutionException("Invalid equality condition: " + condition);
        }

        String left = extractValue(parts[0].trim(), context);
        String right = extractValue(parts[1].trim(), context);

        return Objects.equals(left, right);
    }

    private boolean evaluateComparison(String condition, String operator, Map<String, Object> context) {
        String[] parts = condition.split(operator, 2);
        if (parts.length != 2) {
            throw new NodeExecutionException("Invalid comparison condition: " + condition);
        }

        String leftStr = extractValue(parts[0].trim(), context);
        String rightStr = extractValue(parts[1].trim(), context);

        try {
            double left = Double.parseDouble(leftStr);
            double right = Double.parseDouble(rightStr);

            return switch (operator) {
                case ">" -> left > right;
                case "<" -> left < right;
                case ">=" -> left >= right;
                case "<=" -> left <= right;
                default -> false;
            };
        } catch (NumberFormatException ex) {
            throw new NodeExecutionException(
                "Comparison operator requires numeric values: " + condition, ex);
        }
    }

    private String extractValue(String expression, Map<String, Object> context) {
        expression = expression.trim();
        
        // Remove quotes if present
        if ((expression.startsWith("\"") && expression.endsWith("\"")) ||
            (expression.startsWith("'") && expression.endsWith("'"))) {
            return expression.substring(1, expression.length() - 1);
        }

        // Check if it's a template variable (should have been resolved by TemplateRenderer)
        if (expression.startsWith("{{") && expression.endsWith("}}")) {
            String varName = expression.substring(2, expression.length() - 2).trim();
            Object value = getNestedValue(context, varName);
            if (value == null) {
                throw new NodeExecutionException(
                    "Variable '" + varName + "' not found in context. Available keys: " + context.keySet());
            }
            return value.toString();
        }

        // Check if it's a direct context variable
        Object value = getNestedValue(context, expression);
        if (value != null) {
            return value.toString();
        }

        // Return as-is (might be a literal value)
        return expression;
    }
    
    /**
     * Gets a value from context, supporting nested paths like "defaults.grossIncome"
     */
    private Object getNestedValue(Map<String, Object> context, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        // Check direct key first
        if (context.containsKey(path)) {
            return context.get(path);
        }
        
        // Try nested path (e.g., "defaults.grossIncome")
        if (path.contains(".")) {
            String[] parts = path.split("\\.", 2);
            Object nested = context.get(parts[0]);
            if (nested instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                return getNestedValue(nestedMap, parts[1]);
            }
        }
        
        return null;
    }
}

