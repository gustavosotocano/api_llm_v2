package com.enterprise.agentapi.api;

public record ChatRequest(String userId, String agentSessionId, String message) {}
