package com.ankush.workflowEngine.support;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility to replace {{token}} placeholders with values from the workflow context. */
public final class TemplateRenderer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, Object> context) {
        if (template == null || template.isEmpty() || context == null || context.isEmpty()) {
            return template;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = context.getOrDefault(key, "");
            String replacement = value == null ? "" : value.toString();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
