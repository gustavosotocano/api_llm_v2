package com.enterprise.agentapi.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Agent-facing period input. Raw dates are a distinct semantic failure from an unknown enum.
 */
public final class PeriodExpressions {
    private PeriodExpressions() {}

    public static boolean looksLikeDateRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        var text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            return true;
        }
        if (text.matches(".*\\d{1,2}/\\d{1,2}/\\d{2,4}.*")) {
            return true;
        }
        return text.contains("fromdate")
                || text.contains("todate")
                || text.contains("from_date")
                || text.contains("to_date")
                || text.contains("start_date")
                || text.contains("end_date");
    }

    public static List<String> semanticPeriods() {
        return Arrays.stream(PeriodOption.values()).map(Enum::name).toList();
    }
}
