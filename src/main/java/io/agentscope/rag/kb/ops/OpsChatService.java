package io.agentscope.rag.kb.ops;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.rag.kb.chat.RagChatSupport;
import io.agentscope.rag.kb.config.AgentProperties;
import io.agentscope.rag.kb.config.SimpleRagProperties;
import io.agentscope.rag.kb.store.QdrantDocMaintenance;
import io.agentscope.rag.kb.web.dto.ChatRequest;
import io.agentscope.rag.kb.web.dto.ChatResponse;
import io.agentscope.rag.kb.web.dto.DocumentDto;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OpsChatService {

    private static final Logger log = LoggerFactory.getLogger(OpsChatService.class);

    private final KnowledgeBaseRegistry registry;
    private final AgentProperties agentProperties;
    private final SimpleRagProperties simpleRagProperties;
    private final RagChatSupport ragChatSupport;
    private final Toolkit toolkit;
    private final Map<String, ReActAgent> agentsByKb = new ConcurrentHashMap<>();

    public OpsChatService(
            KnowledgeBaseRegistry registry,
            AgentProperties agentProperties,
            SimpleRagProperties simpleRagProperties,
            RagChatSupport ragChatSupport,
            @Autowired(required = false) Toolkit kbAgentToolkit) {
        this.registry = registry;
        this.agentProperties = agentProperties;
        this.simpleRagProperties = simpleRagProperties;
        this.ragChatSupport = ragChatSupport;
        this.toolkit = kbAgentToolkit != null ? kbAgentToolkit : new Toolkit();
    }

    public ChatResponse chat(String kbId, ChatRequest request) {
        if (!agentProperties.isEnabled()) {
            throw new IllegalStateException("Agent chat is disabled (agentscope.agent.enabled=false)");
        }
        String apiKey = agentProperties.getDashscopeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("agentscope.agent.dashscope-api-key is required");
        }

        KnowledgeBaseContext ctx = registry.require(kbId);
        long pointCount = ctx.maintenance().map(QdrantDocMaintenance::countDocuments).orElse(-1L);
        if (pointCount == 0) {
            String collectionName = ctx.descriptor().getIndexName();
            throw new IllegalStateException(
                    "知识库「"
                            + ctx.descriptor().getDisplayName()
                            + "」(id="
                            + kbId
                            + ") 的 Qdrant 集合 "
                            + collectionName
                            + " 中尚无向量 chunk。"
                            + " 请在运维 UI「文档」页选中该知识库后入库，"
                            + "或调用 POST /api/v1/ops/knowledge-bases/"
                            + kbId
                            + "/documents（勿使用默认库 /api/v1/kb/documents）。");
        }
        if (pointCount < 0) {
            log.warn(
                    "Qdrant point count unavailable for kb={}, collection={}, allowing chat",
                    kbId,
                    ctx.descriptor().getIndexName());
        }

        String query = request.getMessage().trim();
        RetrieveConfig retrieveConfig =
                ragChatSupport.resolveRetrieveConfig(ctx.descriptor(), simpleRagProperties);
        List<DocumentDto> retrieved =
                ragChatSupport.retrieveForChat(ctx.knowledge(), query, retrieveConfig);

        if (!ragChatSupport.hasRelevantHits(retrieved)) {
            log.debug("No KB hits for kb={}, query={}, skip LLM", kbId, query);
            return new ChatResponse(
                    query,
                    ragChatSupport.noHitReply(),
                    request.getSessionId(),
                    kbId,
                    ctx.descriptor().getIndexName(),
                    retrieved);
        }

        ReActAgent agent =
                agentsByKb.computeIfAbsent(
                        kbId, id -> buildAgent(registry.require(kbId), apiKey));
        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(query).build())
                        .build();
        Msg reply = agent.call(userMsg).block();
        String text = reply != null ? reply.getTextContent() : "";
        return new ChatResponse(
                query,
                text,
                request.getSessionId(),
                kbId,
                ctx.descriptor().getIndexName(),
                retrieved);
    }

    private ReActAgent buildAgent(KnowledgeBaseContext ctx, String apiKey) {
        RetrieveConfig retrieveConfig =
                ragChatSupport.resolveRetrieveConfig(ctx.descriptor(), simpleRagProperties);
        RAGMode ragMode = agentProperties.getRagMode() != null ? agentProperties.getRagMode() : RAGMode.GENERIC;

        return ReActAgent.builder()
                .name("KbAssistant-" + ctx.descriptor().getId())
                .sysPrompt(agentProperties.getSystemPrompt())
                .model(
                        DashScopeChatModel.builder()
                                .apiKey(apiKey)
                                .modelName(agentProperties.getModelName())
                                .build())
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .knowledge(ctx.knowledge())
                .ragMode(ragMode)
                .retrieveConfig(retrieveConfig)
                .build();
    }
}
