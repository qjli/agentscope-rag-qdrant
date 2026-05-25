package io.agentscope.rag.kb.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.ops.dto.KbRetrieveSettingsResponse;
import io.agentscope.rag.kb.ops.dto.OpsRetrieveRequest;
import io.agentscope.rag.kb.ops.dto.OpsRetrieveResponse;
import io.agentscope.rag.kb.ops.dto.UpdateRetrieveSettingsRequest;
import io.agentscope.rag.kb.store.QdrantDocMaintenance;
import io.agentscope.rag.kb.store.RetrievePayloadFilter;
import io.agentscope.rag.kb.web.dto.DocumentDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpsRetrieveService {

    private final KnowledgeBaseRegistry registry;
    private final EmbeddingModel embeddingModel;
    private final SimpleRagProperties properties;
    private final ObjectMapper objectMapper;

    public OpsRetrieveService(
            KnowledgeBaseRegistry registry,
            EmbeddingModel embeddingModel,
            SimpleRagProperties properties,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OpsRetrieveResponse retrieve(String kbId, OpsRetrieveRequest request) {
        long start = System.currentTimeMillis();
        KnowledgeBaseContext ctx = registry.require(kbId);
        KnowledgeBaseDescriptor descriptor = ctx.descriptor();

        int limit = resolveLimit(request.getLimit(), descriptor);
        double threshold = resolveThreshold(request.getScoreThreshold(), descriptor);
        RetrievePayloadFilter filter = buildFilter(request);

        float[] queryVector = embedQuery(request.getQuery().trim());
        List<DocumentDto> hits;

        if (ctx.maintenance().isPresent()) {
            QdrantDocMaintenance maintenance = ctx.maintenance().get();
            int qdrantLimit = filter.isEmpty() ? limit : Math.min(limit * 3, 100);
            List<QdrantDocMaintenance.VectorSearchHit> raw =
                    maintenance.queryByVector(queryVector, qdrantLimit, threshold, filter);
            hits =
                    raw.stream()
                            .limit(limit)
                            .map(
                                    h ->
                                            DocumentDto.fromQdrantHit(
                                                    h.pointId(), h.score(), h.payload(), objectMapper))
                            .toList();
        } else {
            RetrieveConfig config =
                    RetrieveConfig.builder().limit(limit).scoreThreshold(threshold).build();
            List<Document> documents =
                    ctx.knowledge()
                            .retrieve(request.getQuery().trim(), config)
                            .blockOptional()
                            .orElse(List.of());
            hits = documents.stream().map(DocumentDto::from).toList();
            if (!filter.isEmpty()) {
                hits = hits.stream().filter(d -> matchesDtoFilter(d, filter)).toList();
            }
        }

        int contextChars =
                hits.stream()
                        .map(DocumentDto::getContent)
                        .filter(c -> c != null)
                        .mapToInt(String::length)
                        .sum();

        return new OpsRetrieveResponse(
                request.getQuery().trim(),
                limit,
                threshold,
                filterMap(filter),
                hits.size(),
                System.currentTimeMillis() - start,
                contextChars,
                hits);
    }

    public KbRetrieveSettingsResponse getRetrieveSettings(String kbId) {
        KnowledgeBaseDescriptor descriptor = registry.require(kbId).descriptor();
        SimpleRagProperties.RetrieveProperties defaults = properties.getRetrieve();
        return new KbRetrieveSettingsResponse(
                descriptor.getId(),
                defaults.getLimit(),
                defaults.getScoreThreshold(),
                descriptor.getRetrieveLimit(),
                descriptor.getRetrieveScoreThreshold());
    }

    public KbRetrieveSettingsResponse updateRetrieveSettings(
            String kbId, UpdateRetrieveSettingsRequest request) {
        KnowledgeBaseDescriptor updated = registry.updateRetrieveSettings(kbId, request);
        SimpleRagProperties.RetrieveProperties defaults = properties.getRetrieve();
        return new KbRetrieveSettingsResponse(
                updated.getId(),
                defaults.getLimit(),
                defaults.getScoreThreshold(),
                updated.getRetrieveLimit(),
                updated.getRetrieveScoreThreshold());
    }

    private float[] embedQuery(String query) {
        double[] embedding =
                embeddingModel.embed(TextBlock.builder().text(query).build()).block();
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("Embedding returned empty vector");
        }
        float[] vector = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            vector[i] = (float) embedding[i];
        }
        return vector;
    }

    private int resolveLimit(Integer requestLimit, KnowledgeBaseDescriptor descriptor) {
        if (requestLimit != null) {
            return Math.min(Math.max(requestLimit, 1), 100);
        }
        if (descriptor.getRetrieveLimit() != null) {
            return Math.min(Math.max(descriptor.getRetrieveLimit(), 1), 100);
        }
        return properties.getRetrieve().getLimit();
    }

    private double resolveThreshold(Double requestThreshold, KnowledgeBaseDescriptor descriptor) {
        if (requestThreshold != null) {
            return requestThreshold;
        }
        if (descriptor.getRetrieveScoreThreshold() != null) {
            return descriptor.getRetrieveScoreThreshold();
        }
        return properties.getRetrieve().getScoreThreshold();
    }

    private RetrievePayloadFilter buildFilter(OpsRetrieveRequest request) {
        return new RetrievePayloadFilter(
                request.getDocId(), request.getMaterialType(), request.getCategory(), request.getSource());
    }

    private Map<String, String> filterMap(RetrievePayloadFilter filter) {
        Map<String, String> map = new LinkedHashMap<>();
        if (filter.docId() != null && !filter.docId().isBlank()) {
            map.put("docId", filter.docId());
        }
        if (filter.materialType() != null && !filter.materialType().isBlank()) {
            map.put("materialType", filter.materialType());
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            map.put("category", filter.category());
        }
        if (filter.source() != null && !filter.source().isBlank()) {
            map.put("source", filter.source());
        }
        return map;
    }

    private boolean matchesDtoFilter(DocumentDto dto, RetrievePayloadFilter filter) {
        if (filter.docId() != null
                && !filter.docId().isBlank()
                && (dto.getDocId() == null || !filter.docId().trim().equals(dto.getDocId()))) {
            return false;
        }
        if (filter.materialType() != null
                && !filter.materialType().isBlank()
                && (dto.getMaterialType() == null
                        || !filter.materialType().trim().equalsIgnoreCase(dto.getMaterialType()))) {
            return false;
        }
        if (filter.category() != null
                && !filter.category().isBlank()
                && (dto.getCategory() == null || !filter.category().trim().equals(dto.getCategory()))) {
            return false;
        }
        if (filter.source() != null
                && !filter.source().isBlank()
                && (dto.getSource() == null || !filter.source().trim().equals(dto.getSource()))) {
            return false;
        }
        return true;
    }
}
