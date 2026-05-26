package io.agentscope.rag.kb.faq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.ingest.DocumentIngestRequest;
import io.agentscope.rag.kb.ingest.IngestResult;
import io.agentscope.rag.kb.ops.KnowledgeBaseRegistry;
import io.agentscope.rag.kb.ops.OpsIngestService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class FaqBootstrapAdapter {

    private static final Logger log = LoggerFactory.getLogger(FaqBootstrapAdapter.class);

    private final OpsIngestService opsIngestService;
    private final SimpleRagProperties properties;
    private final ResourceLoader resourceLoader;
    private final KbIndexRegistry registry;
    private final ObjectMapper objectMapper;

    public FaqBootstrapAdapter(
            OpsIngestService opsIngestService,
            SimpleRagProperties properties,
            ResourceLoader resourceLoader,
            KbIndexRegistry registry,
            ObjectMapper objectMapper) {
        this.opsIngestService = opsIngestService;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public LoadResult loadAll() {
        String location = properties.getFaq().getDataLocation();
        try {
            List<FaqItem> items = readFaqItems(location);
            if (items.isEmpty()) {
                throw new IllegalStateException("FAQ data is empty: " + location);
            }

            int totalChunks = 0;
            for (FaqItem item : items) {
                validateItem(item);
                DocumentIngestRequest req = new DocumentIngestRequest();
                req.setDocId(item.getId());
                req.setTitle("FAQ-" + item.getId());
                req.setText(item.toKnowledgeText());
                req.setCategory(item.getCategory());
                Map<String, Object> payload = new HashMap<>();
                payload.put("source", "faq");
                payload.put("question", item.getQuestion());
                req.setPayload(payload);
                IngestResult result =
                        opsIngestService.ingestText(
                                KnowledgeBaseRegistry.DEFAULT_KB_ID, req, true);
                totalChunks += result.chunkCount();
            }

            registry.recordFaqLoad(items.size(), totalChunks);
            log.info(
                    "FAQ loaded into kb={}: items={}, chunks={}, location={}",
                    KnowledgeBaseRegistry.DEFAULT_KB_ID,
                    items.size(),
                    totalChunks,
                    location);
            return new LoadResult(items.size(), totalChunks, location);
        } catch (Exception ex) {
            String message = "Failed to load FAQ: " + ex.getMessage();
            registry.recordFailure(message);
            log.error(message, ex);
            throw new FaqLoadException(message, ex);
        }
    }

    private List<FaqItem> readFaqItems(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("FAQ resource not found: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            String json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            List<FaqItem> items = objectMapper.readValue(json, new TypeReference<List<FaqItem>>() {});
            return items != null ? items : List.of();
        }
    }

    private void validateItem(FaqItem item) {
        if (item.getId() == null || item.getId().isBlank()) {
            throw new IllegalArgumentException("FAQ item id is required");
        }
        if (item.getQuestion() == null || item.getQuestion().isBlank()) {
            throw new IllegalArgumentException("FAQ item question is required: id=" + item.getId());
        }
        if (item.getAnswer() == null || item.getAnswer().isBlank()) {
            throw new IllegalArgumentException("FAQ item answer is required: id=" + item.getId());
        }
    }

    public record LoadResult(int itemCount, int chunkCount, String location) {}

    public static class FaqLoadException extends RuntimeException {
        public FaqLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
