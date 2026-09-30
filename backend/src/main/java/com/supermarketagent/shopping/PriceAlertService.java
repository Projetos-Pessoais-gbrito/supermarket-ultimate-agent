package com.supermarketagent.shopping;

import com.supermarketagent.shopping.PriceAlerts.Alert;
import com.supermarketagent.shopping.PriceAlerts.Type;
import com.supermarketagent.shopping.PurchaseHistory.Purchase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares each product's latest price with what the user usually paid for it recently.
 * Only recent purchases count, so alerts stay relevant and inflation does not trigger them.
 */
@Service
@Transactional(readOnly = true)
public class PriceAlertService {

    /** A difference smaller than this is normal price noise. */
    static final BigDecimal THRESHOLD_PERCENT = BigDecimal.TEN;
    /** The latest purchase must be this recent to be worth an alert. */
    static final int RECENT_DAYS = 60;
    /** Earlier purchases considered for the usual price. */
    static final int HISTORY_DAYS = 180;
    static final int MIN_EARLIER_PURCHASES = 2;

    private final PurchaseHistory history;

    PriceAlertService(PurchaseHistory history) {
        this.history = history;
    }

    public PriceAlerts alerts(long userId, LocalDate today) {
        List<Alert> alerts = new ArrayList<>();
        for (List<Purchase> purchases : history.byProduct(userId).values()) {
            Purchase latest = purchases.getLast();
            if (latest.day().isBefore(today.minusDays(RECENT_DAYS))) {
                continue;
            }
            // Earlier trips only (lines of the same day as the latest purchase are not "earlier")
            List<Purchase> earlier = purchases.stream()
                    .filter(p -> p.day().isBefore(latest.day()))
                    .filter(p -> !p.day().isBefore(latest.day().minusDays(HISTORY_DAYS)))
                    .toList();
            if (earlier.size() < MIN_EARLIER_PURCHASES) {
                continue;
            }
            BigDecimal usual = earlier.stream().map(Purchase::unitPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(earlier.size()), 4, RoundingMode.HALF_UP);
            if (usual.signum() == 0) {
                continue;
            }
            BigDecimal change = latest.unitPrice().subtract(usual).multiply(BigDecimal.valueOf(100))
                    .divide(usual, 1, RoundingMode.HALF_UP);
            if (change.abs().compareTo(THRESHOLD_PERCENT) < 0) {
                continue;
            }
            alerts.add(new Alert(latest.productId(), latest.productName(), change.signum() < 0 ? Type.DEAL : Type.RISE,
                    latest.unitPrice(), usual.setScale(2, RoundingMode.HALF_UP), change, latest.storeName(),
                    latest.day()));
        }
        alerts.sort(Comparator.comparing((Alert alert) -> alert.changePercent().abs()).reversed());
        return new PriceAlerts(alerts);
    }
}
