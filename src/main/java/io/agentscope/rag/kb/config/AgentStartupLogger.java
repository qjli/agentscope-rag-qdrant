package io.agentscope.rag.kb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AgentStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentStartupLogger.class);

    private final AgentProperties agentProperties;

    public AgentStartupLogger(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!agentProperties.isEnabled()) {
            log.warn("Ops RAG chat disabled (agentscope.agent.enabled=false)");
            return;
        }
        if (agentProperties.getDashscopeApiKey() == null
                || agentProperties.getDashscopeApiKey().isBlank()) {
            log.warn("Ops RAG chat: dashscope-api-key not configured");
            return;
        }
        log.info(
                "Ops RAG chat ready: model={}, ragMode={}",
                agentProperties.getModelName(),
                agentProperties.getRagMode());
    }
}
