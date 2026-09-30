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
    private final InflationInsightService inflation;
    private final InsightSummaryService summary;
    private final CategoryProductsService categoryProducts;
    private final Clock clock;

    InsightController(SpendingInsightService spending, ProductPriceInsightService prices,
                      SavingsInsightService savings, BestDayInsightService bestDay,
                      InflationInsightService inflation, InsightSummaryService summary,
                      CategoryProductsService categoryProducts, Clock clock) {
        this.spending = spending;
        this.prices = prices;
        this.savings = savings;
        this.bestDay = bestDay;
        this.inflation = inflation;
        this.summary = summary;
        this.categoryProducts = categoryProducts;
        this.clock = clock;
    }

    @GetMapping("/spending")
    SpendingInsight spending(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(defaultValue = "6") @Min(InsightWindow.ALL) @Max(120) int months) {
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
                           @RequestParam(defaultValue = "3") @Min(InsightWindow.ALL) @Max(120) int months) {
        return savings.savings(userId(jwt), currentMonth(), months);
    }

    @GetMapping("/best-day")
    BestDayInsight bestDay(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam(defaultValue = "365") @Min(30) @Max(3650) int days) {
        return bestDay.bestDay(userId(jwt), clock.instant(), days);
    }

    @GetMapping("/inflation")
    InflationInsight inflation(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return inflation.inflation(userId(jwt), currentMonth(), months);
    }

    /** AI-written tips from the computed insights; {@code available=false} when no AI provider answers. */
    @GetMapping("/summary")
    InsightSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return summary.summary(userId(jwt), currentMonth(), clock.instant());
    }

    /** Products of one category ({@code none} = not categorized yet), same months as the spending insight. */
    @GetMapping("/categories/{category}/products")
    CategoryProducts categoryProducts(@AuthenticationPrincipal Jwt jwt, @PathVariable String category,
                                      @RequestParam(defaultValue = "6") @Min(InsightWindow.ALL) @Max(120) int months) {
        return categoryProducts.products(userId(jwt), category, currentMonth(), months)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown category"));
    }

    private YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(InsightPeriod.SAO_PAULO));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
