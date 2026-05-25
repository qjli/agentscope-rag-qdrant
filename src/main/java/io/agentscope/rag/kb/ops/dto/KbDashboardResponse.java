package io.agentscope.rag.kb.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KbDashboardResponse(
        @JsonProperty("knowledgeBaseId") String kbId,
        String displayName,
        @JsonProperty("indexName") String collectionName,
        String storeType,
        @JsonProperty("qdrantLocation") String qdrantLocation,
        @JsonProperty("qdrantPing") boolean qdrantPing,
        long chunkCount,
        long documentCount,
        @JsonProperty("materialDistribution") List<MaterialStat> materialStats) {}
