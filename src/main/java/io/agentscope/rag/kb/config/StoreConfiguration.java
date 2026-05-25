package io.agentscope.rag.kb.config;

import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.QdrantStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "agentscope.rag.simple", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StoreConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "agentscope.rag.simple",
            name = "store-type",
            havingValue = "qdrant",
            matchIfMissing = true)
    public QdrantStore qdrantStore(SimpleRagProperties properties) throws VectorStoreException {
        SimpleRagProperties.QdrantProperties qdrant = properties.getQdrant();
        int dimensions = properties.getEmbedding().getDimensions();
        log.info(
                "Creating QdrantStore location={} collection={} dimensions={}",
                qdrant.getLocation(),
                qdrant.getCollectionName(),
                dimensions);

        QdrantStore.Builder builder =
                QdrantStore.builder()
                        .location(qdrant.getLocation())
                        .collectionName(qdrant.getCollectionName())
                        .dimensions(dimensions)
                        .useTransportLayerSecurity(qdrant.isUseTls())
                        .checkCompatibility(qdrant.isCheckCompatibility());

        if (qdrant.hasApiKey()) {
            builder.apiKey(qdrant.getApiKey());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentscope.rag.simple", name = "store-type", havingValue = "memory")
    public VDBStoreBase inMemoryStore(SimpleRagProperties properties) {
        int dimensions = properties.getEmbedding().getDimensions();
        log.info("Creating InMemoryStore dimensions={}", dimensions);
        return InMemoryStore.builder().dimensions(dimensions).build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "agentscope.rag.simple",
            name = "store-type",
            havingValue = "qdrant",
            matchIfMissing = true)
    public VDBStoreBase kbVectorStore(QdrantStore qdrantStore) {
        return qdrantStore;
    }
}
