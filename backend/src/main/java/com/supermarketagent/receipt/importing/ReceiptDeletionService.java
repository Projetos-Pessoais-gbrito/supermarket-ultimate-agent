package com.supermarketagent.receipt.importing;

import com.supermarketagent.receipt.persistence.ReceiptRepository;
import com.supermarketagent.receipt.query.ReceiptNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes a user's receipt (e.g. imported by mistake). Stores and products are shared and stay. */
@Service
public class ReceiptDeletionService {

    private final ReceiptRepository receipts;

    ReceiptDeletionService(ReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Transactional
    public void delete(long userId, long receiptId) {
        if (receipts.deleteOwned(receiptId, userId) == 0) {
            throw new ReceiptNotFoundException(receiptId);
        }
    }
}
