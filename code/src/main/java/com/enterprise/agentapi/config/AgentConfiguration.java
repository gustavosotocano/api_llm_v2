package com.enterprise.agentapi.config;

import com.enterprise.agentapi.agent.AgentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
