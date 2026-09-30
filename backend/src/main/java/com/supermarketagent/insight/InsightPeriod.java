package com.supermarketagent.insight;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

/** Calendar months in São Paulo time: receipts show local time and users think in local months. */
public final class InsightPeriod {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private InsightPeriod() {
    }

    public static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(SAO_PAULO).toInstant();
    }

    public static Instant endOf(YearMonth month) {
        return startOf(month.plusMonths(1));
    }
}
