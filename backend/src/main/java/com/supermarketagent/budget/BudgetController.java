package com.supermarketagent.budget;

import com.supermarketagent.catalog.ProductCategory;
import com.supermarketagent.insight.InsightPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BudgetController {

    private final BudgetService budgets;
    private final Clock clock;

    BudgetController(BudgetService budgets, Clock clock) {
        this.budgets = budgets;
        this.clock = clock;
    }

    @GetMapping("/api/budget")
    BudgetSettings settings(@AuthenticationPrincipal Jwt jwt) {
        return budgets.settings(userId(jwt));
    }

    /** Categories a limit can be set for, in display order. */
    @GetMapping("/api/budget/categories")
    List<CategoryOption> categories() {
        return Arrays.stream(ProductCategory.values())
                .map(category -> new CategoryOption(category.name(), category.label()))
                .toList();
    }

    /** Replaces all limits; send {@code overall: null} and no categories to remove the budget. */
    @PutMapping("/api/budget")
    BudgetSettings replace(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
        List<BudgetSettings.CategoryLimit> categories = request.categories() == null ? List.of()
                : request.categories().stream()
                        .map(limit -> new BudgetSettings.CategoryLimit(limit.category(), null, limit.limit()))
                        .toList();
        return budgets.replace(userId(jwt), new BudgetSettings(request.overall(), categories));
    }

    /** This month's spending against the limits, in São Paulo time. */
    @GetMapping("/api/insights/budget")
    BudgetStatus status(@AuthenticationPrincipal Jwt jwt) {
        return budgets.status(userId(jwt), LocalDate.now(clock.withZone(InsightPeriod.SAO_PAULO)));
    }

    @ExceptionHandler(InvalidBudgetException.class)
    ProblemDetail invalidBudget(InvalidBudgetException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    record CategoryOption(String category, String label) {
    }

    record BudgetRequest(BigDecimal overall, @Size(max = 20) List<@Valid CategoryLimitRequest> categories) {
    }

    record CategoryLimitRequest(@NotNull String category, @NotNull BigDecimal limit) {
    }
}
