package io.agentscope.rag.kb.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.QdrantStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.agentscope.rag.kb.config.OpsDataPaths;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.ops.dto.UpdateRetrieveSettingsRequest;
import io.agentscope.rag.kb.store.QdrantDocMaintenance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseRegistry {

    public static final String DEFAULT_KB_ID = "default";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRegistry.class);

    private final SimpleRagProperties properties;
    private final OpsDataPaths opsDataPaths;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final Optional<VDBStoreBase> defaultVectorStore;
    private final Optional<QdrantDocMaintenance> defaultMaintenance;

    private final Map<String, KnowledgeBaseDescriptor> descriptors = new ConcurrentHashMap<>();
    private final Map<String, RuntimeKnowledgeBase> runtimes = new ConcurrentHashMap<>();

    public KnowledgeBaseRegistry(
            SimpleRagProperties properties,
            OpsDataPaths opsDataPaths,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper,
            @Autowired(required = false) VDBStoreBase kbVectorStore,
            @Autowired(required = false) QdrantDocMaintenance qdrantMaintenance) {
        this.properties = properties;
        this.opsDataPaths = opsDataPaths;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.defaultVectorStore = Optional.ofNullable(kbVectorStore);
        this.defaultMaintenance = Optional.ofNullable(qdrantMaintenance);
    }

    @PostConstruct
    void init() throws IOException {
        loadPersistedDescriptors();
        registerBuiltInDefault();
        for (KnowledgeBaseDescriptor descriptor : List.copyOf(descriptors.values())) {
            if (properties.getStoreType() != SimpleRagProperties.StoreType.QDRANT
                    && !DEFAULT_KB_ID.equals(descriptor.getId())) {
                continue;
            }
            ensureRuntime(descriptor);
        }
        log.info(
                "Knowledge bases loaded: {} (registry={})",
                descriptors.keySet(),
                opsDataPaths.getRegistryFile());
    }

    private void registerBuiltInDefault() {
        SimpleRagProperties.QdrantProperties qdrant = properties.getQdrant();
        KnowledgeBaseDescriptor existing = descriptors.get(DEFAULT_KB_ID);
        if (existing == null) {
            descriptors.put(
                    DEFAULT_KB_ID,
                    new KnowledgeBaseDescriptor(
                            DEFAULT_KB_ID,
                            "默认知识库",
                            qdrant.getCollectionName(),
                            "来自 application.yml 的默认 Qdrant 集合",
                            Instant.now(),
                            true));
        } else {
            existing.setDisplayName(
                    existing.getDisplayName() != null ? existing.getDisplayName() : "默认知识库");
            existing.setIndexName(qdrant.getCollectionName());
            existing.setBuiltIn(true);
            if (existing.getDescription() == null || existing.getDescription().isBlank()) {
                existing.setDescription("来自 application.yml 的默认 Qdrant 集合");
            }
        }
    }

    @PreDestroy
    void shutdown() {
        for (RuntimeKnowledgeBase runtime : runtimes.values()) {
            closeQuietly(runtime);
        }
        runtimes.clear();
    }

    public List<KnowledgeBaseDescriptor> listDescriptors() {
        return new ArrayList<>(descriptors.values());
    }

    public KnowledgeBaseContext require(String kbId) {
        KnowledgeBaseDescriptor descriptor =
                descriptors.get(kbId != null ? kbId.trim() : null);
        if (descriptor == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + kbId);
        }
        RuntimeKnowledgeBase runtime = ensureRuntime(descriptor);
        return new KnowledgeBaseContext(descriptor, runtime.knowledge(), runtime.maintenance());
    }

    public KnowledgeBaseContext requireDefault() {
        return require(DEFAULT_KB_ID);
    }

    public KnowledgeBaseDescriptor updateRetrieveSettings(
            String kbId, UpdateRetrieveSettingsRequest request) {
        KnowledgeBaseDescriptor descriptor = descriptors.get(kbId != null ? kbId.trim() : null);
        if (descriptor == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + kbId);
        }
        if (request.getRetrieveLimit() != null) {
            descriptor.setRetrieveLimit(request.getRetrieveLimit());
        }
        if (request.getRetrieveScoreThreshold() != null) {
            descriptor.setRetrieveScoreThreshold(request.getRetrieveScoreThreshold());
        }
        persistDescriptors();
        return descriptor;
    }

    public KnowledgeBaseDescriptor create(String id, String displayName, String collectionName, String description) {
        String normalizedId = normalizeId(id);
        if (descriptors.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Knowledge base already exists: " + normalizedId);
        }
        String normalizedCollection = normalizeCollectionName(collectionName);
        if (descriptors.values().stream().anyMatch(d -> d.getIndexName().equals(normalizedCollection))) {
            throw new IllegalArgumentException("Qdrant collection already used: " + normalizedCollection);
        }
        if (properties.getStoreType() != SimpleRagProperties.StoreType.QDRANT) {
            throw new IllegalStateException("Multiple knowledge bases require store-type=qdrant");
        }

        KnowledgeBaseDescriptor descriptor =
                new KnowledgeBaseDescriptor(
                        normalizedId,
                        displayName != null && !displayName.isBlank() ? displayName.trim() : normalizedId,
                        normalizedCollection,
                        description,
                        Instant.now(),
                        false);
        descriptors.put(normalizedId, descriptor);
        ensureRuntime(descriptor);
        persistDescriptors();
        log.info("Registered knowledge base id={} collection={}", normalizedId, normalizedCollection);
        return descriptor;
    }

    private void loadPersistedDescriptors() throws IOException {
        Path file = opsDataPaths.getRegistryFile();
        if (!Files.exists(file)) {
            log.info("No registry file yet at {}", file);
            return;
        }
        List<KnowledgeBaseDescriptor> loaded =
                objectMapper.readValue(file.toFile(), new TypeReference<List<KnowledgeBaseDescriptor>>() {});
        int count = 0;
        for (KnowledgeBaseDescriptor descriptor : loaded) {
            if (descriptor.getId() == null || descriptor.getId().isBlank()) {
                continue;
            }
            if (DEFAULT_KB_ID.equals(descriptor.getId())) {
                descriptors.put(descriptor.getId(), descriptor);
                continue;
            }
            if (properties.getStoreType() == SimpleRagProperties.StoreType.QDRANT) {
                descriptors.put(descriptor.getId(), descriptor);
                count++;
            }
        }
        log.info("Loaded {} custom knowledge base(s) from {}", count, file);
    }

    private void persistDescriptors() {
        Path file = opsDataPaths.getRegistryFile();
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), listDescriptors());
            log.debug("Persisted {} knowledge base(s) to {}", descriptors.size(), file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist knowledge base registry to " + file, e);
        }
    }

    private RuntimeKnowledgeBase ensureRuntime(KnowledgeBaseDescriptor descriptor) {
        return runtimes.computeIfAbsent(descriptor.getId(), id -> createRuntime(descriptor));
    }

    private RuntimeKnowledgeBase createRuntime(KnowledgeBaseDescriptor descriptor) {
        if (DEFAULT_KB_ID.equals(descriptor.getId()) && defaultVectorStore.isPresent()) {
            SimpleKnowledge knowledge =
                    SimpleKnowledge.builder()
                            .embeddingModel(embeddingModel)
                            .embeddingStore(defaultVectorStore.get())
                            .build();
            return new RuntimeKnowledgeBase(knowledge, defaultMaintenance, null);
        }

        if (properties.getStoreType() != SimpleRagProperties.StoreType.QDRANT) {
            throw new IllegalStateException(
                    "Only the default knowledge base is available when store-type=memory");
        }

        try {
            SimpleRagProperties.QdrantProperties qdrant = properties.getQdrant();
            QdrantStore.Builder builder =
                    QdrantStore.builder()
                            .location(qdrant.getLocation())
                            .collectionName(descriptor.getIndexName())
                            .dimensions(properties.getEmbedding().getDimensions())
                            .useTransportLayerSecurity(qdrant.isUseTls())
                            .checkCompatibility(qdrant.isCheckCompatibility());
            if (qdrant.hasApiKey()) {
                builder.apiKey(qdrant.getApiKey());
            }
            QdrantStore store = builder.build();
            SimpleKnowledge knowledge =
                    SimpleKnowledge.builder().embeddingModel(embeddingModel).embeddingStore(store).build();
            QdrantDocMaintenance maintenance =
                    new QdrantDocMaintenance(properties, objectMapper, descriptor.getIndexName());
            maintenance.ensurePayloadIndexes();
            return new RuntimeKnowledgeBase(knowledge, Optional.of(maintenance), store);
        } catch (VectorStoreException e) {
            throw new IllegalStateException("Failed to create Qdrant store for " + descriptor.getId(), e);
        }
    }

    private static void closeQuietly(RuntimeKnowledgeBase runtime) {
        if (runtime.qdrantStore() != null) {
            try {
                runtime.qdrantStore().close();
            } catch (Exception e) {
                log.warn("Failed to close QdrantStore: {}", e.getMessage());
            }
        }
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Knowledge base id is required");
        }
        String normalized = id.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid knowledge base id");
        }
        return normalized;
    }

    private static String normalizeCollectionName(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("collectionName is required");
        }
        String normalized = collectionName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid collection name");
        }
        return normalized;
    }

    private record RuntimeKnowledgeBase(
            Knowledge knowledge,
            Optional<QdrantDocMaintenance> maintenance,
            QdrantStore qdrantStore) {}
}
