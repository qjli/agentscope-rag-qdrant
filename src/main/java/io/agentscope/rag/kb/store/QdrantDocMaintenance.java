package io.agentscope.rag.kb.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Qdrant 集合运维（REST）：连通性、统计、按 {@code doc_id} 删除、文档列表。
 * AgentScope {@link io.agentscope.core.rag.store.QdrantStore} 的 delete 按 point id，运维侧统一走 REST 过滤删除。
 */
@Component
@ConditionalOnProperty(
        prefix = "agentscope.rag.simple",
        name = "store-type",
        havingValue = "qdrant",
        matchIfMissing = true)
public class QdrantDocMaintenance {

    private static final Logger log = LoggerFactory.getLogger(QdrantDocMaintenance.class);

    private final String baseUrl;
    private final String collectionName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final int configuredDimensions;

    @Autowired
    public QdrantDocMaintenance(SimpleRagProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, properties.getQdrant().getCollectionName());
    }

    @PostConstruct
    void initDefaultCollectionIndexes() {
        ensurePayloadIndexes();
    }

    /** 由 {@link io.agentscope.rag.kb.ops.KnowledgeBaseRegistry} 按集合名创建，非 Spring Bean。 */
    public QdrantDocMaintenance(
            SimpleRagProperties properties, ObjectMapper objectMapper, String collectionName) {
        SimpleRagProperties.QdrantProperties qdrant = properties.getQdrant();
        this.baseUrl = stripTrailingSlash(qdrant.getLocation());
        this.collectionName = collectionName;
        this.objectMapper = objectMapper;
        this.apiKey = qdrant.hasApiKey() ? qdrant.getApiKey() : null;
        this.configuredDimensions = properties.getEmbedding().getDimensions();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        log.info("QdrantDocMaintenance REST client location={} collection={}", baseUrl, collectionName);
    }

    public boolean ping() {
        try {
            HttpResponse<String> response =
                    httpClient.send(request("/healthz").GET().build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.warn("Qdrant ping failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean collectionExists() {
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request("/collections/" + collectionName).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Qdrant collection check failed: {}", e.getMessage());
            return false;
        }
    }

    public long countDocuments() {
        try {
            if (!collectionExists()) {
                return 0;
            }
            String body = objectMapper.writeValueAsString(Map.of("exact", true));
            HttpResponse<String> response =
                    httpClient.send(
                            request("/collections/" + collectionName + "/points/count")
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return -1;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("result").path("count").asLong(-1);
        } catch (Exception e) {
            log.warn("Qdrant count failed: {}", e.getMessage());
            return -1;
        }
    }

    public long deleteByDocId(String docId) {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId is required");
        }
        try {
            if (!collectionExists()) {
                return 0;
            }
            long before = countDocuments();
            String body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "filter",
                                    Map.of(
                                            "must",
                                            List.of(
                                                    Map.of(
                                                            "key",
                                                            "doc_id",
                                                            "match",
                                                            Map.of("value", docId))))));
            HttpResponse<String> response =
                    httpClient.send(
                            request("/collections/" + collectionName + "/points/delete")
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "points/delete failed: HTTP " + response.statusCode() + " " + response.body());
            }
            long after = countDocuments();
            long deleted = Math.max(0, before - after);
            log.debug("deleteByDocId doc_id={} deleted≈{}", docId, deleted);
            return deleted;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete doc_id=" + docId + " from Qdrant", e);
        }
    }

    public long countUniqueDocIds() {
        return listDocGroups(500).size();
    }

    /** 为常用 payload 字段创建索引（已存在则忽略）。 */
    public void ensurePayloadIndexes() {
        if (!collectionExists()) {
            return;
        }
        createPayloadIndex("doc_id", Map.of("type", "keyword"));
        createPayloadIndex("payload.material_type", Map.of("type", "keyword"));
        createPayloadIndex("payload.category", Map.of("type", "keyword"));
        createPayloadIndex("payload.source", Map.of("type", "keyword"));
        createPayloadIndex("payload.source_file", Map.of("type", "keyword"));
    }

    public QdrantCollectionInfo getCollectionInfo() {
        if (!collectionExists()) {
            return new QdrantCollectionInfo(
                    collectionName,
                    "missing",
                    0,
                    configuredDimensions,
                    null,
                    null,
                    false,
                    List.of());
        }
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request("/collections/" + collectionName).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return new QdrantCollectionInfo(
                        collectionName, "error", -1, configuredDimensions, null, null, false, List.of());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            String status = result.path("status").asText("unknown");
            long points = result.path("points_count").asLong(-1);
            JsonNode vectors = result.path("config").path("params").path("vectors");
            Integer vectorSize = null;
            String distance = null;
            if (vectors.has("size")) {
                vectorSize = vectors.get("size").asInt();
                distance = vectors.path("distance").asText(null);
            } else if (vectors.isObject() && vectors.size() > 0) {
                JsonNode first = vectors.elements().next();
                vectorSize = first.path("size").asInt(0);
                distance = first.path("distance").asText(null);
            }
            boolean dimMatch =
                    vectorSize == null || vectorSize <= 0 || vectorSize == configuredDimensions;
            List<String> indexes = new ArrayList<>();
            JsonNode payloadSchema = result.path("payload_schema");
            if (payloadSchema.isObject()) {
                payloadSchema.fieldNames().forEachRemaining(indexes::add);
            }
            return new QdrantCollectionInfo(
                    collectionName,
                    status,
                    points,
                    configuredDimensions,
                    vectorSize,
                    distance,
                    dimMatch,
                    indexes);
        } catch (Exception e) {
            log.warn("Failed to read collection info: {}", e.getMessage());
            return new QdrantCollectionInfo(
                    collectionName, "error", -1, configuredDimensions, null, null, false, List.of());
        }
    }

    /**
     * 向量检索（REST query），支持 payload 过滤。
     */
    public List<VectorSearchHit> queryByVector(
            float[] queryVector, int limit, double scoreThreshold, RetrievePayloadFilter filter) {
        if (!collectionExists()) {
            return List.of();
        }
        try {
            var body = objectMapper.createObjectNode();
            var queryArr = body.putArray("query");
            for (float v : queryVector) {
                queryArr.add(v);
            }
            body.put("limit", limit);
            body.put("score_threshold", scoreThreshold);
            body.put("with_payload", true);
            body.put("with_vector", false);
            Map<String, Object> qdrantFilter = buildQdrantFilter(filter);
            if (qdrantFilter != null) {
                body.set("filter", objectMapper.valueToTree(qdrantFilter));
            }

            HttpResponse<String> response =
                    httpClient.send(
                            request("/collections/" + collectionName + "/points/query")
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("points/query failed: HTTP " + response.statusCode());
            }
            JsonNode points = objectMapper.readTree(response.body()).path("result").path("points");
            List<VectorSearchHit> hits = new ArrayList<>();
            if (!points.isArray()) {
                return hits;
            }
            for (JsonNode point : points) {
                JsonNode payload = point.path("payload");
                double score = point.path("score").asDouble(0);
                String id = point.path("id").isTextual() ? point.get("id").asText() : point.path("id").toString();
                hits.add(new VectorSearchHit(id, score, payload));
            }
            if (filter != null && !filter.isEmpty()) {
                hits = hits.stream().filter(h -> matchesPayloadFilter(h.payload(), filter)).toList();
            }
            return hits;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Vector query failed for collection=" + collectionName, e);
        }
    }

    /**
     * 滚动拉取 points，按 doc_id 聚合，供仪表盘文档列表使用。
     */
    public List<DocGroupSummary> listDocGroups(int maxDocs) {
        if (!collectionExists()) {
            return List.of();
        }
        Map<String, DocGroupSummary> groups = new LinkedHashMap<>();
        JsonNode pageOffset = null;
        int pageSize = 128;
        int safety = 0;

        try {
            while (groups.size() < maxDocs && safety < 200) {
                safety++;
                var body = objectMapper.createObjectNode();
                body.put("limit", pageSize);
                body.putPOJO("with_payload", List.of("doc_id", "chunk_id", "payload", "content"));
                body.put("with_vector", false);
                if (pageOffset != null && !pageOffset.isNull()) {
                    body.set("offset", pageOffset);
                }

                HttpResponse<String> response =
                        httpClient.send(
                                request("/collections/" + collectionName + "/points/scroll")
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    throw new IllegalStateException("scroll failed: HTTP " + response.statusCode());
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode result = root.path("result");
                JsonNode points = result.path("points");
                if (!points.isArray() || points.isEmpty()) {
                    break;
                }

                for (JsonNode point : points) {
                    JsonNode payload = point.path("payload");
                    String docId = payload.path("doc_id").asText(null);
                    if (docId == null || docId.isBlank()) {
                        continue;
                    }
                    DocGroupSummary existing = groups.get(docId);
                    if (existing == null) {
                        groups.put(docId, parseGroup(docId, payload, 1));
                    } else {
                        groups.put(docId, existing.withChunkCount(existing.chunkCount() + 1));
                    }
                    if (groups.size() >= maxDocs) {
                        break;
                    }
                }

                pageOffset = result.get("next_page_offset");
                if (pageOffset == null || pageOffset.isNull()) {
                    break;
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to scroll collection=" + collectionName, e);
        }

        return new ArrayList<>(groups.values());
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getLocation() {
        return baseUrl;
    }

    private DocGroupSummary parseGroup(String docId, JsonNode payload, long chunkCount) {
        PayloadMeta meta = PayloadMetaParser.parse(payload, objectMapper);
        return new DocGroupSummary(
                docId,
                meta.title() != null ? meta.title() : docId,
                meta.materialType() != null ? meta.materialType() : "TEXT",
                meta.category(),
                chunkCount,
                meta.sourceFile(),
                meta.ingestedAt());
    }

    PayloadMeta extractPayloadMeta(JsonNode payload) {
        return PayloadMetaParser.parse(payload, objectMapper);
    }

    private void createPayloadIndex(String fieldName, Map<String, Object> fieldSchema) {
        try {
            Map<String, Object> body =
                    Map.of("field_name", fieldName, "field_schema", fieldSchema);
            HttpResponse<String> response =
                    httpClient.send(
                            request("/collections/" + collectionName + "/index")
                                    .header("Content-Type", "application/json")
                                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.debug(
                        "Payload index {} on {} skipped: HTTP {} {}",
                        fieldName,
                        collectionName,
                        response.statusCode(),
                        response.body());
            } else {
                log.info("Payload index ensured: {} on {}", fieldName, collectionName);
            }
        } catch (Exception e) {
            log.warn("Failed to create payload index {}: {}", fieldName, e.getMessage());
        }
    }

    private Map<String, Object> buildQdrantFilter(RetrievePayloadFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> must = new ArrayList<>();
        if (filter.docId() != null && !filter.docId().isBlank()) {
            must.add(Map.of("key", "doc_id", "match", Map.of("value", filter.docId().trim())));
        }
        List<Map<String, Object>> nestedMust = new ArrayList<>();
        if (filter.materialType() != null && !filter.materialType().isBlank()) {
            nestedMust.add(
                    Map.of("key", "material_type", "match", Map.of("value", filter.materialType().trim())));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            nestedMust.add(Map.of("key", "category", "match", Map.of("value", filter.category().trim())));
        }
        if (filter.source() != null && !filter.source().isBlank()) {
            nestedMust.add(Map.of("key", "source", "match", Map.of("value", filter.source().trim())));
        }
        if (!nestedMust.isEmpty()) {
            must.add(Map.of("nested", Map.of("key", "payload", "filter", Map.of("must", nestedMust))));
        }
        if (must.isEmpty()) {
            return null;
        }
        return Map.of("must", must);
    }

    private boolean matchesPayloadFilter(JsonNode payload, RetrievePayloadFilter filter) {
        PayloadMeta meta = extractPayloadMeta(payload);
        if (filter.docId() != null
                && !filter.docId().isBlank()
                && !filter.docId().trim().equals(payload.path("doc_id").asText(null))) {
            return false;
        }
        if (filter.materialType() != null
                && !filter.materialType().isBlank()
                && (meta.materialType() == null
                        || !filter.materialType().trim().equalsIgnoreCase(meta.materialType()))) {
            return false;
        }
        if (filter.category() != null
                && !filter.category().isBlank()
                && (meta.category() == null || !filter.category().trim().equals(meta.category()))) {
            return false;
        }
        if (filter.source() != null
                && !filter.source().isBlank()
                && (meta.source() == null || !filter.source().trim().equals(meta.source()))) {
            return false;
        }
        return true;
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
        if (apiKey != null) {
            builder.header("api-key", apiKey);
        }
        return builder;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record DocGroupSummary(
            String docId,
            String title,
            String materialType,
            String category,
            long chunkCount,
            String sourceFile,
            String ingestedAt) {

        DocGroupSummary withChunkCount(long newCount) {
            return new DocGroupSummary(docId, title, materialType, category, newCount, sourceFile, ingestedAt);
        }
    }

    public record VectorSearchHit(String pointId, double score, JsonNode payload) {}

    public record PayloadMeta(
            String title,
            String materialType,
            String category,
            String source,
            String sourceFile,
            String ingestedAt) {}
}
