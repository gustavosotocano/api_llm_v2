package com.enterprise.agentapi;

import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = ToolCallbackConverterAutoConfiguration.class)
public class EnterpriseAgentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseAgentApiApplication.class, args);
    }
}
