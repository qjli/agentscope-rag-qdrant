package io.agentscope.rag.kb.web.dto;

import java.time.Instant;

public class KbIndexStatusResponse {

    private boolean ready;
    private String storeType;
    private String collectionName;
    private String qdrantLocation;
    private boolean qdrantPing;
    private long qdrantPointCount;
    private int lastIngestDocumentCount;
    private int lastIngestChunkCount;
    private Instant lastIngestAt;
    private String lastError;
    private String faqDataLocation;

    public KbIndexStatusResponse(
            boolean ready,
            String storeType,
            String collectionName,
            String qdrantLocation,
            boolean qdrantPing,
            long qdrantPointCount,
            int lastIngestDocumentCount,
            int lastIngestChunkCount,
            Instant lastIngestAt,
            String lastError,
            String faqDataLocation) {
        this.ready = ready;
        this.storeType = storeType;
        this.collectionName = collectionName;
        this.qdrantLocation = qdrantLocation;
        this.qdrantPing = qdrantPing;
        this.qdrantPointCount = qdrantPointCount;
        this.lastIngestDocumentCount = lastIngestDocumentCount;
        this.lastIngestChunkCount = lastIngestChunkCount;
        this.lastIngestAt = lastIngestAt;
        this.lastError = lastError;
        this.faqDataLocation = faqDataLocation;
    }

    public boolean isReady() {
        return ready;
    }

    public String getStoreType() {
        return storeType;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getQdrantLocation() {
        return qdrantLocation;
    }

    public boolean isQdrantPing() {
        return qdrantPing;
    }

    public long getQdrantPointCount() {
        return qdrantPointCount;
    }

    public int getLastIngestDocumentCount() {
        return lastIngestDocumentCount;
    }

    public int getLastIngestChunkCount() {
        return lastIngestChunkCount;
    }

    public Instant getLastIngestAt() {
        return lastIngestAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getFaqDataLocation() {
        return faqDataLocation;
    }
}
