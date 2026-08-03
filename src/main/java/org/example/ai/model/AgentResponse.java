package org.example.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AgentResponse {
    @JsonProperty("content")
    private String content;

    @JsonProperty("type")
    private String type = "text";

    public AgentResponse() {}

    public AgentResponse(String content) {
        this.content = content;
    }

    // Getter 和 Setter
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
