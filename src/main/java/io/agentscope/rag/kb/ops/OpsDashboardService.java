package io.agentscope.rag.kb.ops;

import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.ops.dto.KbDashboardResponse;
import io.agentscope.rag.kb.ops.dto.KbDocumentRow;
import io.agentscope.rag.kb.ops.dto.KnowledgeBaseSummary;
import io.agentscope.rag.kb.ops.dto.MaterialStat;
import io.agentscope.rag.kb.store.QdrantCollectionInfo;
import io.agentscope.rag.kb.store.QdrantDocMaintenance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpsDashboardService {

    private final KnowledgeBaseRegistry registry;
    private final SimpleRagProperties properties;

    public OpsDashboardService(KnowledgeBaseRegistry registry, SimpleRagProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public List<KnowledgeBaseSummary> listKnowledgeBases() {
        List<KnowledgeBaseSummary> summaries = new ArrayList<>();
        for (KnowledgeBaseDescriptor descriptor : registry.listDescriptors()) {
            summaries.add(toSummary(descriptor));
        }
        return summaries;
    }

    public KbDashboardResponse dashboard(String kbId) {
        KnowledgeBaseContext ctx = registry.require(kbId);
        KnowledgeBaseDescriptor descriptor = ctx.descriptor();

        long chunkCount = -1;
        long docCount = -1;
        boolean qdrantPing = false;
        String qdrantStatus = "n/a";
        Integer qdrantVectorSize = null;
        boolean dimensionMatch = true;
        String qdrantDistance = null;
        List<String> payloadIndexes = List.of();

        if (ctx.maintenance().isPresent()) {
            QdrantDocMaintenance maintenance = ctx.maintenance().get();
            chunkCount = maintenance.countDocuments();
            docCount = maintenance.countUniqueDocIds();
            qdrantPing = maintenance.ping();
            QdrantCollectionInfo info = maintenance.getCollectionInfo();
            qdrantStatus = info.status();
            qdrantVectorSize = info.collectionVectorSize();
            dimensionMatch = info.dimensionMatch();
            qdrantDistance = info.distance();
            payloadIndexes = info.payloadIndexes();
        }

        int defaultLimit = properties.getRetrieve().getLimit();
        double defaultThreshold = properties.getRetrieve().getScoreThreshold();
        Integer effectiveLimit = descriptor.getRetrieveLimit() != null ? descriptor.getRetrieveLimit() : defaultLimit;
        Double effectiveThreshold =
                descriptor.getRetrieveScoreThreshold() != null
                        ? descriptor.getRetrieveScoreThreshold()
                        : defaultThreshold;

        List<MaterialStat> materialStats = computeMaterialStats(ctx);
        return new KbDashboardResponse(
                descriptor.getId(),
                descriptor.getDisplayName(),
                descriptor.getIndexName(),
                properties.getStoreType().name(),
                properties.getQdrant().getLocation(),
                qdrantPing,
                chunkCount,
                docCount,
                materialStats,
                qdrantStatus,
                qdrantVectorSize,
                properties.getEmbedding().getDimensions(),
                dimensionMatch,
                qdrantDistance,
                payloadIndexes,
                effectiveLimit,
                effectiveThreshold);
    }

    public List<KbDocumentRow> listDocuments(String kbId, int limit, String materialType, String category) {
        KnowledgeBaseContext ctx = registry.require(kbId);
        QdrantDocMaintenance maintenance =
                ctx.maintenance()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Document list requires qdrant store"));
        List<QdrantDocMaintenance.DocGroupSummary> groups =
                maintenance.listDocGroups(Math.min(Math.max(limit, 1), 500));
        List<KbDocumentRow> rows = new ArrayList<>();
        for (QdrantDocMaintenance.DocGroupSummary group : groups) {
            if (materialType != null
                    && !materialType.isBlank()
                    && (group.materialType() == null
                            || !materialType.equalsIgnoreCase(group.materialType()))) {
                continue;
            }
            if (category != null
                    && !category.isBlank()
                    && (group.category() == null || !category.trim().equals(group.category()))) {
                continue;
            }
            rows.add(
                    new KbDocumentRow(
                            group.docId(),
                            group.title(),
                            group.materialType(),
                            group.category(),
                            group.chunkCount(),
                            group.sourceFile(),
                            group.ingestedAt(),
                            healthHint(group.chunkCount())));
        }
        return rows;
    }

    private static String healthHint(long chunkCount) {
        if (chunkCount <= 0) {
            return "无 chunk";
        }
        if (chunkCount > 200) {
            return "chunk 过多，建议拆分文档";
        }
        return null;
    }

    public KnowledgeBaseSummary toSummary(KnowledgeBaseDescriptor descriptor) {
        KnowledgeBaseContext ctx = registry.require(descriptor.getId());
        long chunks = ctx.maintenance().map(QdrantDocMaintenance::countDocuments).orElse(-1L);
        long docs = ctx.maintenance().map(QdrantDocMaintenance::countUniqueDocIds).orElse(-1L);
        boolean ping = ctx.maintenance().map(QdrantDocMaintenance::ping).orElse(false);
        return new KnowledgeBaseSummary(
                descriptor.getId(),
                descriptor.getDisplayName(),
                descriptor.getIndexName(),
                descriptor.getDescription(),
                descriptor.isBuiltIn(),
                descriptor.getCreatedAt(),
                ping,
                chunks,
                docs);
    }

    private List<MaterialStat> computeMaterialStats(KnowledgeBaseContext ctx) {
        Map<String, Long> counts = new HashMap<>();
        for (KbDocumentRow row : listDocuments(ctx.descriptor().getId(), 100, null, null)) {
            counts.merge(row.materialType() != null ? row.materialType() : "TEXT", 1L, Long::sum);
        }
        List<MaterialStat> stats = new ArrayList<>();
        counts.forEach((type, count) -> stats.add(new MaterialStat(type, count)));
        return stats;
    }
}
