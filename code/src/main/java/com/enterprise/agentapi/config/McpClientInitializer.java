package com.enterprise.agentapi.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpClientInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(McpClientInitializer.class);

    private final List<McpSyncClient> mcpSyncClients;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    public McpClientInitializer(List<McpSyncClient> mcpSyncClients,
                                SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.mcpSyncClients = mcpSyncClients;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        for (var client : mcpSyncClients) {
            if (!client.isInitialized()) {
                var result = client.initialize();
                log.info("MCP bridge client initialized: server={}",
                        result.serverInfo() == null ? "unknown" : result.serverInfo().name());
            }
        }
        toolCallbackProvider.invalidateCache();
        var tools = toolCallbackProvider.getToolCallbacks();
        log.info("MCP bridge ready — {} tools discovered from local MCP server", tools.length);
        for (var tool : tools) {
            log.info("  MCP tool: {}", tool.getToolDefinition().name());
        }
    }
}
