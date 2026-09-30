package com.supermarketagent.insight;

import com.supermarketagent.insight.ProductPriceInsight.PriceHistory;
import com.supermarketagent.insight.ProductPriceInsight.ProductSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/insights")
class InsightController {

    private final SpendingInsightService spending;
    private final ProductPriceInsightService prices;
    private final SavingsInsightService savings;
    private final BestDayInsightService bestDay;
    private final Clock clock;

    InsightController(SpendingInsightService spending, ProductPriceInsightService prices,
                      SavingsInsightService savings, BestDayInsightService bestDay, Clock clock) {
        this.spending = spending;
        this.prices = prices;
        this.savings = savings;
        this.bestDay = bestDay;
        this.clock = clock;
    }

    @GetMapping("/spending")
    SpendingInsight spending(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return spending.spending(userId(jwt), currentMonth(), months);
    }

    @GetMapping("/products")
    List<ProductSummary> products(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return prices.products(userId(jwt), limit);
    }

    /** 404 when the user never bought the product, so other users' purchases are not revealed. */
    @GetMapping("/products/{productId}/prices")
    PriceHistory priceHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        return prices.history(userId(jwt), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @GetMapping("/savings")
    SavingsInsight savings(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam(defaultValue = "90") @Min(7) @Max(365) int days) {
        return savings.savings(userId(jwt), clock.instant(), days);
    }

    @GetMapping("/best-day")
    BestDayInsight bestDay(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam(defaultValue = "365") @Min(30) @Max(730) int days) {
        return bestDay.bestDay(userId(jwt), clock.instant(), days);
    }

    private YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(InsightPeriod.SAO_PAULO));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
