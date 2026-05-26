package io.agentscope.rag.kb.ingest;

import io.agentscope.rag.kb.ops.MaterialType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 文档 chunk 写入 Qdrant 时的 payload 字段组装（运维入库与 FAQ 引导共用）。 */
public final class DocumentPayloadBuilder {

    private DocumentPayloadBuilder() {}

    public static Map<String, Object> forOpsIngest(
            DocumentIngestRequest request, MaterialType materialType, String sourceFile) {
        Map<String, Object> payload = new HashMap<>();
        if (request.getPayload() != null) {
            payload.putAll(request.getPayload());
        }
        payload.putIfAbsent("doc_id", request.getDocId());
        payload.put("material_type", materialType.name());
        payload.put("reader", materialType.getReaderLabel());
        payload.put("ingested_at", Instant.now().toString());
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            payload.putIfAbsent("title", request.getTitle());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            payload.putIfAbsent("category", request.getCategory().trim());
        }
        if (sourceFile != null) {
            payload.put("source_file", sourceFile);
        }
        return payload;
    }

    public static Map<String, Object> forOpsFileIngest(
            String docId,
            MaterialType materialType,
            String title,
            String category,
            String sourceFile,
            Map<String, Object> extraPayload) {
        DocumentIngestRequest request = new DocumentIngestRequest();
        request.setDocId(docId);
        request.setTitle(title);
        request.setCategory(category);
        request.setPayload(extraPayload);
        request.setText(".");
        return forOpsIngest(request, materialType, sourceFile);
    }
}
