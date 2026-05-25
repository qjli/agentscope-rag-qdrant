package io.agentscope.rag.kb.store;

import java.util.List;

public record QdrantCollectionInfo(
        String collectionName,
        String status,
        long pointsCount,
        int configuredDimensions,
        Integer collectionVectorSize,
        String distance,
        boolean dimensionMatch,
        List<String> payloadIndexes) {}
