package com.jusmartride.routing;

import jakarta.validation.constraints.NotBlank;

public class ShortestPathRequest {

    @NotBlank(message = "Source location ID is required")
    private String sourceId;

    @NotBlank(message = "Target location ID is required")
    private String targetId;

    public ShortestPathRequest() {
    }

    public ShortestPathRequest(String sourceId, String targetId) {
        this.sourceId = sourceId;
        this.targetId = targetId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
}
