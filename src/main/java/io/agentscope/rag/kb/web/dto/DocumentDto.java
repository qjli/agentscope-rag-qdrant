package io.agentscope.rag.kb.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.rag.kb.store.PayloadMetaParser;
import java.util.Map;

public class DocumentDto {

    private String id;
    private Double score;
    private String content;
    private String docId;
    private String chunkId;
    private String title;
    private String category;
    private String source;
    private String sourceFile;
    private String materialType;
    private Map<String, Object> payload;

    public static DocumentDto from(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setScore(document.getScore());
        DocumentMetadata metadata = document.getMetadata();
        if (metadata != null) {
            dto.setContent(metadata.getContentText());
            dto.setDocId(metadata.getDocId());
            dto.setChunkId(metadata.getChunkId());
            dto.setPayload(metadata.getPayload());
            enrichFromPayloadMap(dto, metadata.getPayload());
        }
        return dto;
    }

    public static DocumentDto fromQdrantHit(String pointId, double score, JsonNode payload, ObjectMapper mapper) {
        DocumentDto dto = new DocumentDto();
        dto.setId(pointId);
        dto.setScore(score);
        dto.setDocId(payload.path("doc_id").asText(null));
        dto.setChunkId(payload.path("chunk_id").asText(null));
        dto.setContent(PayloadMetaParser.extractContentText(payload));
        var meta = PayloadMetaParser.parse(payload, mapper);
        if (meta.title() != null) {
            dto.setTitle(meta.title());
        }
        if (meta.materialType() != null) {
            dto.setMaterialType(meta.materialType());
        }
        if (meta.category() != null) {
            dto.setCategory(meta.category());
        }
        if (meta.source() != null) {
            dto.setSource(meta.source());
        }
        if (meta.sourceFile() != null) {
            dto.setSourceFile(meta.sourceFile());
        }
        return dto;
    }

    private static void enrichFromPayloadMap(DocumentDto dto, Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        if (payload.get("title") != null) {
            dto.setTitle(String.valueOf(payload.get("title")));
        }
        if (payload.get("category") != null) {
            dto.setCategory(String.valueOf(payload.get("category")));
        }
        if (payload.get("source") != null) {
            dto.setSource(String.valueOf(payload.get("source")));
        }
        if (payload.get("source_file") != null) {
            dto.setSourceFile(String.valueOf(payload.get("source_file")));
        }
        if (payload.get("material_type") != null) {
            dto.setMaterialType(String.valueOf(payload.get("material_type")));
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
        enrichFromPayloadMap(this, payload);
    }
}
