package io.agentscope.rag.kb.health;

import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.faq.KbIndexRegistry;
import io.agentscope.rag.kb.store.QdrantDocMaintenance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class KbQdrantHealthIndicator implements HealthIndicator {

    private final SimpleRagProperties properties;
    private final KbIndexRegistry registry;
    private final QdrantDocMaintenance qdrantMaintenance;

    public KbQdrantHealthIndicator(
            SimpleRagProperties properties,
            KbIndexRegistry registry,
            @Autowired(required = false) QdrantDocMaintenance qdrantMaintenance) {
        this.properties = properties;
        this.registry = registry;
        this.qdrantMaintenance = qdrantMaintenance;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.outOfService().withDetail("reason", "RAG disabled").build();
        }

        if (properties.getStoreType() == SimpleRagProperties.StoreType.MEMORY) {
            return Health.up()
                    .withDetail("store", "InMemoryStore")
                    .withDetail("chunks", registry.getChunkCount())
                    .build();
        }

        if (qdrantMaintenance == null) {
            return Health.down().withDetail("reason", "Qdrant maintenance not configured").build();
        }

        if (registry.getLastError() != null) {
            return Health.down().withDetail("lastError", registry.getLastError()).build();
        }

        boolean ping = qdrantMaintenance.ping();
        long count = qdrantMaintenance.countDocuments();

        if (!ping) {
            return Health.down()
                    .withDetail("reason", "Qdrant ping failed")
                    .withDetail("location", properties.getQdrant().getLocation())
                    .build();
        }

        Health.Builder builder =
                Health.up()
                        .withDetail("store", "QdrantStore")
                        .withDetail("collection", qdrantMaintenance.getCollectionName())
                        .withDetail("pointCount", count);

        if (count <= 0) {
            builder.withDetail(
                    "hint", "Ingest via POST /api/v1/kb/documents or POST /api/v1/faq/reload");
        }
        return builder.build();
    }
}
