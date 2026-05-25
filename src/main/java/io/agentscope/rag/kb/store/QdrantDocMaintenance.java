package io.agentscope.rag.kb.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    @Autowired
    public QdrantDocMaintenance(SimpleRagProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, properties.getQdrant().getCollectionName());
    }

    /** 由 {@link io.agentscope.rag.kb.ops.KnowledgeBaseRegistry} 按集合名创建，非 Spring Bean。 */
    public QdrantDocMaintenance(
            SimpleRagProperties properties, ObjectMapper objectMapper, String collectionName) {
        SimpleRagProperties.QdrantProperties qdrant = properties.getQdrant();
        this.baseUrl = stripTrailingSlash(qdrant.getLocation());
        this.collectionName = collectionName;
        this.objectMapper = objectMapper;
        this.apiKey = qdrant.hasApiKey() ? qdrant.getApiKey() : null;
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
        String title = docId;
        String materialType = "TEXT";
        String sourceFile = null;
        String ingestedAt = null;
        JsonNode custom = payload.path("payload");
        if (custom.isObject()) {
            if (custom.hasNonNull("title")) {
                title = custom.get("title").asText();
            }
            if (custom.hasNonNull("material_type")) {
                materialType = custom.get("material_type").asText();
            }
            if (custom.hasNonNull("source_file")) {
                sourceFile = custom.get("source_file").asText();
            }
            if (custom.hasNonNull("ingested_at")) {
                ingestedAt = custom.get("ingested_at").asText();
            }
        } else if (custom.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(custom.asText());
                title = parsed.path("title").asText(docId);
                materialType = parsed.path("material_type").asText("TEXT");
                sourceFile = parsed.path("source_file").asText(null);
                ingestedAt = parsed.path("ingested_at").asText(null);
            } catch (Exception ignored) {
                // ignore malformed nested payload
            }
        }
        return new DocGroupSummary(docId, title, materialType, chunkCount, sourceFile, ingestedAt);
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
            long chunkCount,
            String sourceFile,
            String ingestedAt) {

        DocGroupSummary withChunkCount(long newCount) {
            return new DocGroupSummary(docId, title, materialType, newCount, sourceFile, ingestedAt);
        }
    }
}
