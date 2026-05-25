package io.agentscope.rag.kb.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PayloadMetaParser {

    private PayloadMetaParser() {}

    public static QdrantDocMaintenance.PayloadMeta parse(JsonNode payload, ObjectMapper mapper) {
        String title = null;
        String materialType = null;
        String category = null;
        String sourceFile = null;
        String ingestedAt = null;
        String source = null;
        JsonNode custom = payload.path("payload");
        if (custom.isObject()) {
            title = textOrNull(custom, "title");
            materialType = textOrNull(custom, "material_type");
            category = textOrNull(custom, "category");
            sourceFile = textOrNull(custom, "source_file");
            ingestedAt = textOrNull(custom, "ingested_at");
            source = textOrNull(custom, "source");
        } else if (custom.isTextual() && mapper != null) {
            try {
                JsonNode parsed = mapper.readTree(custom.asText());
                title = textOrNull(parsed, "title");
                materialType = textOrNull(parsed, "material_type");
                category = textOrNull(parsed, "category");
                sourceFile = textOrNull(parsed, "source_file");
                ingestedAt = textOrNull(parsed, "ingested_at");
                source = textOrNull(parsed, "source");
            } catch (Exception ignored) {
                // ignore malformed nested payload
            }
        }
        return new QdrantDocMaintenance.PayloadMeta(title, materialType, category, source, sourceFile, ingestedAt);
    }

    /**
     * 从 Qdrant point payload 解析正文。
     *
     * <p>AgentScope {@link io.agentscope.core.rag.store.QdrantStore} 将 ContentBlock 序列化为 JSON 对象存入
     * {@code content} 字段（通常为 {@code {"type":"text","text":"..."}}），不能对其直接 {@code asText()}。
     */
    public static String extractContentText(JsonNode payload) {
        if (payload == null || payload.isMissingNode()) {
            return null;
        }
        JsonNode contentNode = payload.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isObject()) {
            String text = textOrNull(contentNode, "text");
            if (text != null && !text.isBlank()) {
                return text;
            }
            text = textOrNull(contentNode, "content");
            if (text != null && !text.isBlank()) {
                return text;
            }
            // Qdrant REST 偶发 struct 形态
            JsonNode fields = contentNode.path("struct_value").path("fields");
            if (fields.isObject()) {
                JsonNode textField = fields.path("text");
                if (textField.has("string_value")) {
                    return textField.get("string_value").asText();
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
