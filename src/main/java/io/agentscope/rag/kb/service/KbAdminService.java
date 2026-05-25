package io.agentscope.rag.kb.service;

import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.faq.FaqBootstrapAdapter;
import io.agentscope.rag.kb.faq.KbIndexRegistry;
import io.agentscope.rag.kb.store.QdrantDocMaintenance;
import io.agentscope.rag.kb.web.dto.KbIndexStatusResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KbAdminService {

    private final FaqBootstrapAdapter faqBootstrapAdapter;
    private final KbIndexRegistry registry;
    private final SimpleRagProperties properties;
    private final Optional<QdrantDocMaintenance> qdrantMaintenance;

    public KbAdminService(
            FaqBootstrapAdapter faqBootstrapAdapter,
            KbIndexRegistry registry,
            SimpleRagProperties properties,
            @Autowired(required = false) QdrantDocMaintenance qdrantMaintenance) {
        this.faqBootstrapAdapter = faqBootstrapAdapter;
        this.registry = registry;
        this.properties = properties;
        this.qdrantMaintenance = Optional.ofNullable(qdrantMaintenance);
    }

    public KbIndexStatusResponse status() {
        return buildStatus(null, null, null);
    }

    public KbIndexStatusResponse reloadFaq() {
        if (!properties.getFaq().isReloadEnabled()) {
            throw new IllegalStateException("FAQ reload is disabled by configuration");
        }
        FaqBootstrapAdapter.LoadResult result = faqBootstrapAdapter.loadAll();
        return buildStatus(result.itemCount(), result.chunkCount(), result.location());
    }

    private KbIndexStatusResponse buildStatus(Integer faqItems, Integer chunks, String location) {
        long pointCount = qdrantMaintenance.map(QdrantDocMaintenance::countDocuments).orElse(-1L);
        boolean qdrantPing = qdrantMaintenance.map(QdrantDocMaintenance::ping).orElse(false);
        String collectionName =
                qdrantMaintenance.map(QdrantDocMaintenance::getCollectionName).orElse("n/a");

        boolean ready = pointCount > 0 || registry.isReady();

        return new KbIndexStatusResponse(
                ready,
                properties.getStoreType().name(),
                collectionName,
                properties.getQdrant().getLocation(),
                qdrantPing,
                pointCount,
                faqItems != null ? faqItems : registry.getDocumentCount(),
                chunks != null ? chunks : registry.getChunkCount(),
                registry.getLastIngestAt(),
                registry.getLastError(),
                location != null ? location : properties.getFaq().getDataLocation());
    }
}
