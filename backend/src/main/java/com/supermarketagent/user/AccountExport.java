package com.supermarketagent.user;

import com.supermarketagent.receipt.query.ReceiptDetails;
import java.time.Instant;
import java.util.List;

/** Everything personal the app keeps about a user (LGPD right to data portability). */
public record AccountExport(Instant exportedAt, Account account, List<ReceiptDetails> receipts) {

    public record Account(long id, String email, Instant createdAt) {
    }
}
