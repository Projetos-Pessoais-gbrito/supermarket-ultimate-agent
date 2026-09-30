package com.supermarketagent.insight;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
class InsightController {

    private final SpendingInsightService spending;
    private final Clock clock;

    InsightController(SpendingInsightService spending, Clock clock) {
        this.spending = spending;
        this.clock = clock;
    }

    @GetMapping("/spending")
    SpendingInsight spending(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return spending.spending(userId(jwt), currentMonth(), months);
    }

    private YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(InsightPeriod.SAO_PAULO));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
