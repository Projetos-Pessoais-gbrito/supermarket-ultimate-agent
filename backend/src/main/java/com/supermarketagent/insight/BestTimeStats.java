package com.supermarketagent.insight;

import com.supermarketagent.insight.BestTimeInsight.Finding;
import com.supermarketagent.insight.BestTimeInsight.Group;
import com.supermarketagent.insight.BestTimeInsight.Status;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether one group of purchases (a part of the month, a weekday) is really cheaper than
 * the others at the same store, or whether the difference is just chance. Each purchase is a ratio:
 * price paid / usual price of that product at that store (1.0 = usual).
 *
 * <p>A group is a pattern only when it is at least {@link #MIN_PERCENT_CHEAPER}% cheaper than the
 * rest (smaller differences are not worth waiting for) and Welch's t-test says the gap is unlikely
 * to be chance. Weekdays use a stricter threshold: with seven groups, one is often cheapest by luck.
 */
final class BestTimeStats {

    /** Below this, a group is too small to tell a pattern from a lucky promotion. */
    static final int MIN_SAMPLES_PER_GROUP = 8;
    /** Smaller differences are not worth changing when you shop. */
    static final double MIN_PERCENT_CHEAPER = 3.0;
    /** About 95% confidence for one comparison (parts of the month: three groups). */
    static final double MIN_T_FEW_GROUPS = 2.0;
    /** Stricter when there are many groups to pick the cheapest from (weekdays). */
    static final double MIN_T_MANY_GROUPS = 2.7;

    private BestTimeStats() {
    }

    /**
     * Running totals of the ratios in one group, enough for means and variances.
     *
     * @param sum        sum of the ratios
     * @param sumSquares sum of the squared ratios
     */
    record Sample(String key, String label, long count, double sum, double sumSquares) {

        double mean() {
            return sum / count;
        }

        /** Sample variance; 0 for a single purchase. */
        double variance() {
            return count < 2 ? 0 : Math.max(0, (sumSquares - count * mean() * mean()) / (count - 1));
        }

        Sample plus(Sample other) {
            return new Sample(key, label, count + other.count, sum + other.sum, sumSquares + other.sumSquares);
        }
    }

    /** @param samples groups with at least one purchase, in calendar order */
    static Finding decide(List<Sample> samples) {
        List<Group> groups = samples.stream()
                .map(sample -> new Group(sample.key(), sample.label(),
                        percent(sample.mean() - 1), (int) sample.count()))
                .toList();
        List<Sample> eligible = samples.stream().filter(s -> s.count() >= MIN_SAMPLES_PER_GROUP).toList();
        if (eligible.size() < 2) {
            return new Finding(Status.NOT_ENOUGH_DATA, null, null, groups);
        }
        double minT = samples.size() > 3 ? MIN_T_MANY_GROUPS : MIN_T_FEW_GROUPS;

        record Candidate(Sample sample, double percentCheaper) {
        }
        Optional<Candidate> best = eligible.stream()
                .map(group -> {
                    Sample rest = samples.stream().filter(s -> s != group)
                            .reduce(new Sample("", "", 0, 0, 0), Sample::plus);
                    double cheaper = (1 - group.mean() / rest.mean()) * 100;
                    double t = welchT(group, rest);
                    return cheaper >= MIN_PERCENT_CHEAPER && t >= minT ? new Candidate(group, cheaper) : null;
                })
                .filter(candidate -> candidate != null)
                .max(Comparator.comparingDouble(Candidate::percentCheaper));

        if (best.isEmpty()) {
            return new Finding(Status.NO_PATTERN, null, null, groups);
        }
        Group bestGroup = groups.stream().filter(g -> g.key().equals(best.get().sample().key())).findFirst()
                .orElseThrow();
        return new Finding(Status.PATTERN, bestGroup, round(best.get().percentCheaper()), groups);
    }

    /** How many standard errors the group's mean is below the rest's (positive = cheaper). */
    static double welchT(Sample group, Sample rest) {
        double standardError = Math.sqrt(group.variance() / group.count() + rest.variance() / rest.count());
        double gap = rest.mean() - group.mean();
        if (standardError == 0) {
            return gap > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return gap / standardError;
    }

    private static BigDecimal percent(double fraction) {
        return round(fraction * 100);
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
