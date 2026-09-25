package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.AgentWorkflow;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "enterprise.agent")
public class AgentProperties {
    private RateLimit rateLimit = new RateLimit();
    private Confirmation confirmation = new Confirmation();
    private Governance governance = new Governance();
    private Budget budget = new Budget();
    private Tools tools = new Tools();
    private Identity identity = new Identity();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Confirmation getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(Confirmation confirmation) {
        this.confirmation = confirmation;
    }

    public Governance getGovernance() {
        return governance;
    }

    public void setGovernance(Governance governance) {
        this.governance = governance;
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public static class RateLimit {
        private int maxRequestsPerWindow = 20;
        private int windowSeconds = 60;
        private Map<String, ToolLimit> perTool = defaultPerToolLimits();
        private LoopDetection loopDetection = new LoopDetection();

        public int getMaxRequestsPerWindow() {
            return maxRequestsPerWindow;
        }

        public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public Map<String, ToolLimit> getPerTool() {
            return perTool;
        }

        public void setPerTool(Map<String, ToolLimit> perTool) {
            this.perTool = perTool;
        }

        public LoopDetection getLoopDetection() {
            return loopDetection;
        }

        public void setLoopDetection(LoopDetection loopDetection) {
            this.loopDetection = loopDetection;
        }

        public ToolLimit resolveToolLimit(String toolName) {
            return perTool.get(toolName);
        }

        private static Map<String, ToolLimit> defaultPerToolLimits() {
            var limits = new LinkedHashMap<String, ToolLimit>();
            limits.put("searchRecurringPayments", toolLimit(15, 60));
            limits.put("cancelRecurringSubscription", toolLimit(5, 60));
            limits.put("proposeCatalogChange", toolLimit(3, 300));
            limits.put("startCustomerReport", toolLimit(3, 120));
            limits.put("getJobStatus", toolLimit(20, 60));
            limits.put("getJobResult", toolLimit(10, 60));
            limits.put("AI_CHAT", toolLimit(10, 60));
            limits.put("GOVERNANCE_APPROVE", toolLimit(10, 60));
            limits.put("GOVERNANCE_REJECT", toolLimit(10, 60));
            return limits;
        }

        private static ToolLimit toolLimit(int max, int window) {
            var limit = new ToolLimit();
            limit.setMaxRequestsPerWindow(max);
            limit.setWindowSeconds(window);
            return limit;
        }
    }

    public static class ToolLimit {
        private int maxRequestsPerWindow = 10;
        private int windowSeconds = 60;

        public int getMaxRequestsPerWindow() {
            return maxRequestsPerWindow;
        }

        public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class LoopDetection {
        private boolean enabled = true;
        private int windowSeconds = 10;
        private int maxSameToolCalls = 5;
        private boolean blockOnLoop = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getMaxSameToolCalls() {
            return maxSameToolCalls;
        }

        public void setMaxSameToolCalls(int maxSameToolCalls) {
            this.maxSameToolCalls = maxSameToolCalls;
        }

        public boolean isBlockOnLoop() {
            return blockOnLoop;
        }

        public void setBlockOnLoop(boolean blockOnLoop) {
            this.blockOnLoop = blockOnLoop;
        }
    }

    public static class Confirmation {
        private int tokenTtlSeconds = 300;

        public int getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(int tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }
    }

    public static class Governance {
        private List<String> allowedReviewers = List.of("catalog-admin", "compliance-officer");

        public List<String> getAllowedReviewers() {
            return allowedReviewers;
        }

        public void setAllowedReviewers(List<String> allowedReviewers) {
            this.allowedReviewers = allowedReviewers;
        }
    }

    public static class Budget {
        private int maxUnitsPerSession = 50;
        private Map<String, Integer> perToolCost = defaultCosts();

        public int getMaxUnitsPerSession() {
            return maxUnitsPerSession;
        }

        public void setMaxUnitsPerSession(int maxUnitsPerSession) {
            this.maxUnitsPerSession = maxUnitsPerSession;
        }

        public Map<String, Integer> getPerToolCost() {
            return perToolCost;
        }

        public void setPerToolCost(Map<String, Integer> perToolCost) {
            this.perToolCost = perToolCost;
        }

        public int costOf(String toolName) {
            return perToolCost.getOrDefault(toolName, 1);
        }

        private static Map<String, Integer> defaultCosts() {
            var costs = new LinkedHashMap<String, Integer>();
            costs.put("searchRecurringPayments", 2);
            costs.put("cancelRecurringSubscription", 5);
            costs.put("proposeCatalogChange", 3);
            costs.put("startCustomerReport", 15);
            costs.put("getJobStatus", 1);
            costs.put("getJobResult", 1);
            costs.put("AI_CHAT", 4);
            return costs;
        }
    }

    public static class Tools {
        private AgentWorkflow defaultWorkflow = AgentWorkflow.READ;

        public AgentWorkflow getDefaultWorkflow() {
            return defaultWorkflow;
        }

        public void setDefaultWorkflow(AgentWorkflow defaultWorkflow) {
            this.defaultWorkflow = defaultWorkflow == null ? AgentWorkflow.READ : defaultWorkflow;
        }
    }

    public static class Identity {
        private int credentialTtlSeconds = 300;
        private List<String> defaultUserScopes = List.of(
                "transactions:read", "subscriptions:write", "catalog:propose", "jobs:run");

        public int getCredentialTtlSeconds() {
            return credentialTtlSeconds;
        }

        public void setCredentialTtlSeconds(int credentialTtlSeconds) {
            this.credentialTtlSeconds = credentialTtlSeconds;
        }

        public List<String> getDefaultUserScopes() {
            return defaultUserScopes;
        }

        public void setDefaultUserScopes(List<String> defaultUserScopes) {
            this.defaultUserScopes = defaultUserScopes;
        }
    }
}
