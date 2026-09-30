package com.supermarketagent.shopping;

import com.supermarketagent.insight.InsightPeriod;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shopping insights computed from the user's own purchase history. */
@RestController
@RequestMapping("/api/insights")
class ShoppingInsightController {

    private final ShoppingSuggestionService suggestions;
    private final PriceAlertService alerts;
    private final Clock clock;

    ShoppingInsightController(ShoppingSuggestionService suggestions, PriceAlertService alerts, Clock clock) {
        this.suggestions = suggestions;
        this.alerts = alerts;
        this.clock = clock;
    }

    @GetMapping("/shopping-list")
    ShoppingSuggestions shoppingList(@AuthenticationPrincipal Jwt jwt) {
        return suggestions.suggestions(userId(jwt), today());
    }

    @GetMapping("/price-alerts")
    PriceAlerts priceAlerts(@AuthenticationPrincipal Jwt jwt) {
        return alerts.alerts(userId(jwt), today());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(InsightPeriod.SAO_PAULO));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
