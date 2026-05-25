package io.agentscope.rag.kb.chat;

import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.rag.kb.config.AgentProperties;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.ops.KnowledgeBaseDescriptor;
import io.agentscope.rag.kb.web.dto.DocumentDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RagChatSupport {

    private final AgentProperties agentProperties;

    public RagChatSupport(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    public String noHitReply() {
        return agentProperties.getNoHitReply().trim();
    }

    public RetrieveConfig agentRetrieveConfig() {
        AgentProperties.RetrieveProperties retrieve = agentProperties.getRetrieve();
        return RetrieveConfig.builder()
                .limit(retrieve.getLimit())
                .scoreThreshold(retrieve.getScoreThreshold())
                .build();
    }

    public RetrieveConfig resolveRetrieveConfig(
            KnowledgeBaseDescriptor descriptor, SimpleRagProperties simpleRag) {
        int limit =
                descriptor != null && descriptor.getRetrieveLimit() != null
                        ? Math.min(Math.max(descriptor.getRetrieveLimit(), 1), 50)
                        : simpleRag.getRetrieve().getLimit();
        double threshold =
                descriptor != null && descriptor.getRetrieveScoreThreshold() != null
                        ? descriptor.getRetrieveScoreThreshold()
                        : simpleRag.getRetrieve().getScoreThreshold();
        return RetrieveConfig.builder().limit(limit).scoreThreshold(threshold).build();
    }

    public List<DocumentDto> retrieveForChat(Knowledge knowledge, String query) {
        return retrieveForChat(knowledge, query, agentRetrieveConfig());
    }

    public List<DocumentDto> retrieveForChat(
            Knowledge knowledge, String query, RetrieveConfig config) {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<Document> documents =
                knowledge.retrieve(trimmed, config).blockOptional().orElse(List.of());
        return documents.stream().map(DocumentDto::from).toList();
    }

    public boolean hasRelevantHits(List<DocumentDto> retrieved) {
        return retrieved != null && !retrieved.isEmpty();
    }
}
