package io.agentscope.rag.kb.ops.dto;

import io.agentscope.rag.kb.web.dto.DocumentDto;
import java.util.List;
import java.util.Map;

public record OpsRetrieveResponse(
        String query,
        int limit,
        double scoreThreshold,
        Map<String, String> appliedFilters,
        int hitCount,
        long latencyMs,
        int estimatedContextChars,
        List<DocumentDto> hits) {}
