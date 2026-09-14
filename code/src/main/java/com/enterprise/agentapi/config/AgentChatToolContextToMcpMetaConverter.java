package com.enterprise.agentapi.config;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AgentChatToolContextToMcpMetaConverter implements ToolContextToMcpMetaConverter {
    @Override
    public Map<String, Object> convert(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return Map.of();
        }
        var meta = new HashMap<String, Object>();
        copyIfPresent(toolContext.getContext(), meta, "userId");
        copyIfPresent(toolContext.getContext(), meta, "agentSessionId");
        copyIfPresent(toolContext.getContext(), meta, "identityType");
        meta.put("channel", "AI_CHAT");
        return meta;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        var value = source.get(key);
        if (value != null) {
            var text = value.toString().trim();
            if (!text.isEmpty()) {
                target.put(key, text);
            }
        }
    }
}
