package io.agentscope.rag.kb.config;

import io.agentscope.core.rag.RAGMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agentscope.agent")
public class AgentProperties {

    private boolean enabled = true;

    private String dashscopeApiKey;

    private String modelName = "qwen-plus";

    private RAGMode ragMode = RAGMode.GENERIC;

    private RetrieveProperties retrieve = new RetrieveProperties();

    @NotBlank
    private String systemPrompt = "你是企业知识库助手。请依据知识库内容回答。";

    /** 检索无命中时直接返回给用户，不调用大模型 */
    @NotBlank
    private String noHitReply = "知识库中未提供关于该问题的内容，无法据此作答。请联系人工客服。";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDashscopeApiKey() {
        return dashscopeApiKey;
    }

    public void setDashscopeApiKey(String dashscopeApiKey) {
        this.dashscopeApiKey = dashscopeApiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public RAGMode getRagMode() {
        return ragMode;
    }

    public void setRagMode(RAGMode ragMode) {
        this.ragMode = ragMode;
    }

    public RetrieveProperties getRetrieve() {
        return retrieve;
    }

    public void setRetrieve(RetrieveProperties retrieve) {
        this.retrieve = retrieve;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getNoHitReply() {
        return noHitReply;
    }

    public void setNoHitReply(String noHitReply) {
        this.noHitReply = noHitReply;
    }

    public static class RetrieveProperties {

        @Min(1)
        @Max(50)
        private int limit = 3;

        @Min(0)
        @Max(1)
        private double scoreThreshold = 0.35;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }
    }
}
