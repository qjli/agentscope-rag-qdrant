package io.agentscope.rag.kb.ops.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UpdateRetrieveSettingsRequest {

    @Min(1)
    @Max(100)
    private Integer retrieveLimit;

    @Min(0)
    @Max(1)
    private Double retrieveScoreThreshold;

    public Integer getRetrieveLimit() {
        return retrieveLimit;
    }

    public void setRetrieveLimit(Integer retrieveLimit) {
        this.retrieveLimit = retrieveLimit;
    }

    public Double getRetrieveScoreThreshold() {
        return retrieveScoreThreshold;
    }

    public void setRetrieveScoreThreshold(Double retrieveScoreThreshold) {
        this.retrieveScoreThreshold = retrieveScoreThreshold;
    }
}
