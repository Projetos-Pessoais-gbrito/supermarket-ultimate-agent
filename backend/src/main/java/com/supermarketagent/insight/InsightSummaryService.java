package com.supermarketagent.insight;

import com.supermarketagent.ai.AiClient;
import com.supermarketagent.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns computed insights into short, friendly tips (ADR 0005): every number is calculated here and
 * handed to the AI as facts; the model only writes the text. Answers are cached per set of facts, so
 * the AI is called again only when the user's data changes.
 */
@Service
public class InsightSummaryService {

    static final int MAX_TIPS = 3;
    private static final int MAX_TIP_LENGTH = 200;
    private static final int CACHE_SIZE = 500;
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final Logger log = LoggerFactory.getLogger(InsightSummaryService.class);

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("tips", Map.of(
                    "type", "array",
                    "items", Map.of("type", "string"),
                    "minItems", 1,
                    "maxItems", MAX_TIPS)),
            "required", List.of("tips"));

    private final AiClient ai;
    private final SpendingInsightService spending;
    private final SavingsInsightService savings;
    private final BestDayInsightService bestDay;
    private final InflationInsightService inflation;
    private final Map<String, List<String>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    InsightSummaryService(AiClient ai, SpendingInsightService spending, SavingsInsightService savings,
                          BestDayInsightService bestDay, InflationInsightService inflation) {
        this.ai = ai;
        this.spending = spending;
        this.savings = savings;
        this.bestDay = bestDay;
        this.inflation = inflation;
    }

    public InsightSummary summary(long userId, YearMonth currentMonth, Instant now) {
        if (!ai.isAvailable()) {
            return InsightSummary.unavailable();
        }
        List<String> facts = facts(userId, currentMonth, now);
        if (facts.isEmpty()) {
            return new InsightSummary(true, List.of());
        }

        // Keyed on the facts themselves: any change in the user's data produces new tips
        String key = userId + "\n" + String.join("\n", facts);
        List<String> cached = cache.get(key);
        if (cached != null) {
            return new InsightSummary(true, cached);
        }
        try {
            Answer answer = ai.generateJson(prompt(facts), SCHEMA, Answer.class);
            List<String> tips = clean(answer.tips());
            cache.put(key, tips);
            return new InsightSummary(true, tips);
        } catch (AiUnavailableException e) {
            log.warn("Insight summary skipped: {}", e.getMessage());
            return InsightSummary.unavailable();
        }
    }

    void clearCache() {
        cache.clear();
    }

    /** Facts in pt-BR with the numbers already formatted, so the model has nothing to calculate. */
    List<String> facts(long userId, YearMonth currentMonth, Instant now) {
        List<String> facts = new ArrayList<>();
        SpendingInsight spent = spending.spending(userId, currentMonth, 3);
        if (spent.monthly().stream().allMatch(month -> month.receiptCount() == 0)) {
            return facts;
        }

        String month = currentMonth.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        String previousMonth = currentMonth.minusMonths(1).getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        facts.add("Gasto em " + month + ": " + money(spent.currentMonth().total()) + " em "
                + spent.currentMonth().receiptCount() + " notas.");
        if (spent.changePercent() != null) {
            facts.add("Variação em relação a " + previousMonth + ": " + signedPercent(spent.changePercent()) + ".");
        }
        if (!spent.byCategory().isEmpty()) {
            var top = spent.byCategory().getFirst();
            facts.add("Categoria com maior gasto nos últimos 3 meses: " + top.label() + " (" + money(top.total()) + ").");
        }
        if (!spent.byStore().isEmpty()) {
            var top = spent.byStore().getFirst();
            facts.add("Mercado onde mais gastou nos últimos 3 meses: " + top.storeName() + " (" + money(top.total()) + ").");
        }

        SavingsInsight saved = savings.savings(userId, now, 90);
        if (saved.potentialSavings().signum() > 0) {
            facts.add("Economia possível nos últimos 90 dias pagando sempre o menor preço já pago: "
                    + money(saved.potentialSavings()) + ".");
            var product = saved.products().getFirst();
            facts.add("Produto em que mais pagou acima do melhor preço: " + product.name() + " (melhor preço "
                    + money(product.bestUnitPrice()) + " no " + product.bestStoreName() + ", "
                    + money(product.extraPaid()) + " a mais no total).");
        }

        BestDayInsight best = bestDay.bestDay(userId, now, 365);
        if (best.bestPeriod() != null && best.bestPeriod().percentVsAverage().signum() < 0) {
            facts.add("Período do mês com preços mais baixos: " + best.bestPeriod().label().toLowerCase(PT_BR) + " ("
                    + signedPercent(best.bestPeriod().percentVsAverage()) + " em relação à média).");
        }

        var latest = inflation.inflation(userId, currentMonth, 1);
        var basket = latest.monthly().getLast();
        if (basket.changePercent() != null) {
            facts.add("Inflação da sua cesta em " + month + ": " + signedPercent(basket.changePercent()) + " ("
                    + basket.productsCompared() + " produtos comparados).");
        }
        if (!latest.changes().isEmpty() && latest.changes().getFirst().changePercent().signum() > 0) {
            var up = latest.changes().getFirst();
            facts.add("Maior aumento de preço: " + up.name() + " (" + signedPercent(up.changePercent()) + ").");
        }
        return facts;
    }

    private static String prompt(List<String> facts) {
        return """
                Você é um assistente de economia doméstica de um app de notas fiscais de supermercado.
                Escreva de 1 a 3 dicas curtas (no máximo 140 caracteres cada) em português do Brasil,
                em tom amigável e direto, falando com o usuário ("você").
                Use SOMENTE os fatos abaixo. Não invente números, produtos, lojas nem porcentagens.
                Prefira dicas acionáveis (onde ou quando comprar) a apenas repetir os números.

                Fatos:
                - """ + String.join("\n- ", facts);
    }

    private static List<String> clean(List<String> tips) {
        if (tips == null) {
            return List.of();
        }
        return tips.stream()
                .filter(tip -> tip != null && !tip.isBlank())
                .map(String::strip)
                .map(tip -> tip.length() > MAX_TIP_LENGTH ? tip.substring(0, MAX_TIP_LENGTH - 1) + "…" : tip)
                .limit(MAX_TIPS)
                .toList();
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
    }

    private static String signedPercent(BigDecimal value) {
        String number = value.abs().toPlainString().replace('.', ',') + "%";
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "-" : "") + number;
    }

    record Answer(List<String> tips) {
    }
}
