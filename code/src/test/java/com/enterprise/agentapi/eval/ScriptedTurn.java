package com.enterprise.agentapi.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public record ScriptedTurn(String toolName, Map<String, Object> arguments) {
    public static ScriptedTurn of(String toolName, Object... keyValues) {
        var args = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            args.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new ScriptedTurn(toolName, args);
    }
}
