package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import com.ankush.workflowEngine.support.TemplateRenderer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNodeExecutor.class);

    private final JavaMailSender mailSender;

    public EmailNodeExecutor(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public NodeType supportsType() {
        return NodeType.EMAIL;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> snapshot = context.context().snapshot();

        String from = config.get("from") != null ? config.get("from").toString() : null;
        String subjectTemplate = Objects.toString(config.get("subject"), "FlowStack Email");
        String bodyTemplate = Objects.toString(config.get("body"), "Workflow notification");
        Object toValue = config.get("to");
        if (toValue == null) {
            throw new NodeExecutionException("Email node requires 'to' field");
        }
        List<String> recipients = parseAddresses(toValue, snapshot);
        if (recipients.isEmpty()) {
            throw new NodeExecutionException("Email node resolved zero recipients");
        }

        String subject = TemplateRenderer.render(subjectTemplate, snapshot);
        if (subject == null) {
            subject = "";
        }
        String body = TemplateRenderer.render(bodyTemplate, snapshot);
        if (body == null) {
            body = "";
        }

        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String[] toAddresses = normalizeAddresses(recipients);
            if (toAddresses.length == 0) {
                throw new NodeExecutionException("Email node resolved zero recipients");
            }
            helper.setTo(toAddresses);
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setSubject(subject);
            helper.setText(body, false);

            Object ccValue = config.get("cc");
            Object bccValue = config.get("bcc");
            if (ccValue != null) {
                List<String> ccList = parseAddresses(ccValue, snapshot);
                String[] ccArray = normalizeAddresses(ccList);
                if (ccArray.length > 0) {
                    helper.setCc(ccArray);
                }
            }
            if (bccValue != null) {
                List<String> bccList = parseAddresses(bccValue, snapshot);
                String[] bccArray = normalizeAddresses(bccList);
                if (bccArray.length > 0) {
                    helper.setBcc(bccArray);
                }
            }

            mailSender.send(message);
            Map<String, Object> output = Map.of(
                    context.node().getNodeKey() + "::status", "SENT",
                    context.node().getNodeKey() + "::to", String.join(",", recipients));
            context.context().merge(output);
            LOGGER.info("[FlowStack] Email node {} sent to {}", context.node().getNodeKey(), recipients);
            return NodeExecutionResult.completed(output, "email sent");
        } catch (MailException | jakarta.mail.MessagingException ex) {
            LOGGER.error("[FlowStack] Email node {} failed: {}", context.node().getNodeKey(), ex.getMessage());
            throw new NodeExecutionException("Email sending failed", ex);
        }
    }

    private List<String> parseAddresses(Object value, Map<String, Object> snapshot) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object v : iterable) {
                appendAddress(result, v, snapshot);
            }
        } else if (value instanceof String str) {
            for (String part : str.split(",")) {
                appendAddress(result, part, snapshot);
            }
        } else if (value != null) {
            appendAddress(result, value, snapshot);
        }
        return result;
    }

    private void appendAddress(List<String> result, Object raw, Map<String, Object> snapshot) {
        if (raw == null) {
            return;
        }
        String rendered = TemplateRenderer.render(raw.toString(), snapshot);
        if (rendered == null) {
            return;
        }
        String trimmed = rendered.trim();
        if (!trimmed.isEmpty()) {
            result.add(trimmed);
        }
    }

    private String[] normalizeAddresses(List<String> addresses) {
        return addresses.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
