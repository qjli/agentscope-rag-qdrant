package io.agentscope.rag.kb.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record KnowledgeBaseSummary(
        String id,
        String displayName,
        @JsonProperty("indexName") String collectionName,
        String description,
        boolean builtIn,
        Instant createdAt,
        @JsonProperty("qdrantPing") boolean qdrantPing,
        long chunkCount,
        long documentCount) {}
