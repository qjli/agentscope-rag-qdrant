package io.agentscope.rag.kb.ops.dto;

public record KbRetrieveSettingsResponse(
        String knowledgeBaseId,
        int defaultLimit,
        double defaultScoreThreshold,
        Integer retrieveLimit,
        Double retrieveScoreThreshold) {}
