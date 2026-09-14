package com.enterprise.agentapi.mcp;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class McpJsonEncoder {
    private final JsonMapper jsonMapper;

    public McpJsonEncoder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(Object value) {
        return jsonMapper.writeValueAsString(value);
    }
}
