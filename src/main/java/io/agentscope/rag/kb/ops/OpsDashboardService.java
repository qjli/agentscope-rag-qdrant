package io.agentscope.rag.kb.ops;

import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.ops.dto.KbDashboardResponse;
import io.agentscope.rag.kb.ops.dto.KbDocumentRow;
import io.agentscope.rag.kb.ops.dto.KnowledgeBaseSummary;
import io.agentscope.rag.kb.ops.dto.MaterialStat;
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
        if (ctx.maintenance().isPresent()) {
            QdrantDocMaintenance maintenance = ctx.maintenance().get();
            chunkCount = maintenance.countDocuments();
            docCount = maintenance.countUniqueDocIds();
            qdrantPing = maintenance.ping();
        }

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
                materialStats);
    }

    public List<KbDocumentRow> listDocuments(String kbId, int limit) {
        KnowledgeBaseContext ctx = registry.require(kbId);
        QdrantDocMaintenance maintenance =
                ctx.maintenance()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Document list requires qdrant store"));
        List<QdrantDocMaintenance.DocGroupSummary> groups =
                maintenance.listDocGroups(Math.min(Math.max(limit, 1), 200));
        List<KbDocumentRow> rows = new ArrayList<>();
        for (QdrantDocMaintenance.DocGroupSummary group : groups) {
            rows.add(
                    new KbDocumentRow(
                            group.docId(),
                            group.title(),
                            group.materialType(),
                            group.chunkCount(),
                            group.sourceFile(),
                            group.ingestedAt()));
        }
        return rows;
    }

    private KnowledgeBaseSummary toSummary(KnowledgeBaseDescriptor descriptor) {
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
        for (KbDocumentRow row : listDocuments(ctx.descriptor().getId(), 100)) {
            counts.merge(row.materialType() != null ? row.materialType() : "TEXT", 1L, Long::sum);
        }
        List<MaterialStat> stats = new ArrayList<>();
        counts.forEach((type, count) -> stats.add(new MaterialStat(type, count)));
        return stats;
    }
}
