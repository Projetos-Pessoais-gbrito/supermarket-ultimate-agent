package com.supermarketagent.receipt.query;

import com.supermarketagent.catalog.Store;
import com.supermarketagent.receipt.persistence.Receipt;
import com.supermarketagent.receipt.persistence.ReceiptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for receipts; every query is scoped to the owner. */
@Service
@Transactional(readOnly = true)
public class ReceiptQueryService {

    private final ReceiptRepository receipts;

    ReceiptQueryService(ReceiptRepository receipts) {
        this.receipts = receipts;
    }

    public Page<ReceiptSummary> list(long userId, int page, int size) {
        return receipts.findSummariesByUserId(userId, PageRequest.of(page, size));
    }

    public ReceiptDetails details(long userId, long receiptId) {
        Receipt receipt = receipts.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new ReceiptNotFoundException(receiptId));
        Store store = receipt.getStore();
        return new ReceiptDetails(
                receipt.getId(),
                receipt.getAccessKey(),
                receipt.getNumber(),
                receipt.getSeries(),
                receipt.getIssuedAt(),
                new ReceiptDetails.Store(store.getId(), store.getCnpj(), store.getDisplayName(), store.getName(),
                        store.getAddress()),
                receipt.getTotalAmount(),
                receipt.getDiscountAmount(),
                receipt.getApproximateTaxes(),
                receipt.getItems().stream()
                        .map(item -> new ReceiptDetails.Item(
                                item.getLineNumber(),
                                item.getStoreProduct().getStoreCode(),
                                item.getStoreProduct().getDescription(),
                                item.getQuantity(),
                                item.getUnit(),
                                item.getUnitPrice(),
                                item.getTotalPrice()))
                        .toList(),
                receipt.getPayments().stream()
                        .map(payment -> new ReceiptDetails.Payment(payment.getMethod(), payment.getAmount()))
                        .toList());
    }
}
