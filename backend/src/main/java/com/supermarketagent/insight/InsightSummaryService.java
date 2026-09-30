package com.supermarketagent.insight;

import com.supermarketagent.ai.AiClient;
import com.supermarketagent.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns computed insights into short, friendly tips (ADR 0005): every number is calculated here and
 * handed to the AI as facts; the model only writes the text. Tips are stored per set of facts, so the
 * AI is called again only when the user's data (or the prompt) changes, and tips survive restarts.
 */
@Service
public class InsightSummaryService {

    static final int MAX_TIPS = 3;
    private static final int MAX_TIP_LENGTH = 200;
    /** Bump when the prompt changes, so stored tips are rewritten with the new instructions. */
    static final String PROMPT_VERSION = "2";
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
    private final InsightSummaryStore store;

    InsightSummaryService(AiClient ai, SpendingInsightService spending, SavingsInsightService savings,
                          BestDayInsightService bestDay, InflationInsightService inflation,
                          InsightSummaryStore store) {
        this.ai = ai;
        this.spending = spending;
        this.savings = savings;
        this.bestDay = bestDay;
        this.inflation = inflation;
        this.store = store;
    }

    /**
     * @param today the day considered "now" in São Paulo time
     * @param now   the same moment as an instant (best-day window)
     */
    public InsightSummary summary(long userId, LocalDate today, Instant now) {
        if (!ai.isAvailable()) {
            return InsightSummary.unavailable();
        }
        List<String> facts = facts(userId, today, now);
        if (facts.isEmpty()) {
            return new InsightSummary(true, List.of());
        }

        // Keyed on the facts themselves: any change in the user's data produces new tips
        String factsHash = hash(PROMPT_VERSION + "\n" + String.join("\n", facts));
        Optional<List<String>> stored = store.find(userId, factsHash);
        if (stored.isPresent()) {
            return new InsightSummary(true, stored.get());
        }
        try {
            Answer answer = ai.generateJson(prompt(facts), SCHEMA, Answer.class);
            List<String> tips = clean(answer.tips());
            store.save(userId, factsHash, tips);
            return new InsightSummary(true, tips);
        } catch (AiUnavailableException e) {
            log.warn("Insight summary skipped: {}", e.getMessage());
            return InsightSummary.unavailable();
        }
    }

    /** Facts in pt-BR with the numbers already formatted, so the model has nothing to calculate. */
    List<String> facts(long userId, LocalDate today, Instant now) {
        List<String> facts = new ArrayList<>();
        YearMonth currentMonth = YearMonth.from(today);
        SpendingInsight spent = spending.spending(userId, today, 3);
        if (spent.monthly().stream().allMatch(month -> month.receiptCount() == 0)) {
            return facts;
        }

        String month = currentMonth.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        String previousMonth = currentMonth.minusMonths(1).getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        facts.add("Gasto em " + month + ": " + money(spent.currentMonth().total()) + " em "
                + spent.currentMonth().receiptCount() + " notas.");
        if (spent.changePercent() != null) {
            facts.add("Variação em relação a " + previousMonth + " no mesmo período (dias 1 a "
                    + spent.comparedUntilDay() + "): " + signedPercent(spent.changePercent()) + ".");
        }
        if (!spent.byCategory().isEmpty()) {
            var top = spent.byCategory().getFirst();
            facts.add("Categoria com maior gasto nos últimos 3 meses: " + top.label() + " (" + money(top.total()) + ").");
        }

        SavingsInsight saved = savings.savings(userId, currentMonth, 3);
        if (saved.potentialSavings().signum() > 0) {
            facts.add("Economia possível nos últimos 3 meses pagando o menor preço visto até 2 meses antes ou depois: "
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

    static String prompt(List<String> facts) {
        return """
                Você é um assistente de economia doméstica de um app de notas fiscais de supermercado.
                Escreva de 1 a 3 dicas curtas (no máximo 140 caracteres cada) em português do Brasil,
                em tom amigável e direto, falando com o usuário ("você").

                Regras:
                - Use SOMENTE os fatos abaixo. Não invente números, produtos, lojas, datas nem porcentagens.
                - Cada dica traz uma ação concreta (o que comprar, onde ou quando) e o fato que a justifica.
                - Não apenas repita um número e não dê dicas circulares ou óbvias (por exemplo, "compre no
                  mercado onde você mais compra"). Só recomende um mercado quando um fato mostrar preço menor nele.
                - Escreva nomes de produtos de forma legível, sem as abreviações do cupom
                  (ex.: "BEB LACTEA YOPRO 250ML BAUNILHA" vira "bebida láctea YoPro 250 ml de baunilha").
                - Frases naturais e corretas; sem emojis, sem jargão e sem expressões como
                  "economizar o melhor preço".

                Fatos:
                - """ + String.join("\n- ", facts);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
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
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace('\u00a0', ' ');
    }

    private static String signedPercent(BigDecimal value) {
        String number = value.abs().toPlainString().replace('.', ',') + "%";
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "-" : "") + number;
    }

    record Answer(List<String> tips) {
    }
}
