package com.supermarketagent.insight;

import java.util.List;

/**
 * Short tips written by the AI from facts the backend computed.
 *
 * @param available false when no AI provider can answer; the app then hides the card
 * @param tips      up to three tips in pt-BR; empty when there is not enough data yet
 */
public record InsightSummary(boolean available, List<String> tips) {

    static InsightSummary unavailable() {
        return new InsightSummary(false, List.of());
    }
}
