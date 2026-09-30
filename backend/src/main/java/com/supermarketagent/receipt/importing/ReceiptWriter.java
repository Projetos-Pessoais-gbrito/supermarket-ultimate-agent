package com.supermarketagent.receipt.importing;

import com.supermarketagent.catalog.Store;
import com.supermarketagent.catalog.StoreProduct;
import com.supermarketagent.catalog.StoreNames;
import com.supermarketagent.catalog.StoreProductRepository;
import com.supermarketagent.catalog.StoreRepository;
import com.supermarketagent.receipt.persistence.Receipt;
import com.supermarketagent.receipt.persistence.ReceiptItem;
import com.supermarketagent.receipt.persistence.ReceiptPayment;
import com.supermarketagent.receipt.persistence.ReceiptRepository;
import com.supermarketagent.receipt.provider.FetchedReceipt;
import com.supermarketagent.receipt.provider.ParsedReceipt;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persists a fetched receipt in one transaction, reusing existing stores and store products. */
@Component
class ReceiptWriter {

    private final StoreRepository stores;
    private final StoreProductRepository storeProducts;
    private final ReceiptRepository receipts;

    ReceiptWriter(StoreRepository stores, StoreProductRepository storeProducts, ReceiptRepository receipts) {
        this.stores = stores;
        this.storeProducts = storeProducts;
        this.receipts = receipts;
    }

    @Transactional
    long save(long userId, FetchedReceipt fetched, ZoneId timeZone, String sourceUrl) {
        ParsedReceipt parsed = fetched.receipt();
        ParsedReceipt.Store parsedStore = parsed.store();
        long storeId = stores.upsert(parsedStore.cnpj(), parsedStore.name(),
                StoreNames.displayName(parsedStore.cnpj(), parsedStore.name()), parsedStore.address(),
                parsed.accessKey().stateCode());
        Store store = stores.getReferenceById(storeId);

        Receipt receipt = new Receipt(
                userId,
                store,
                parsed.accessKey().value(),
                parsed.number(),
                parsed.series(),
                parsed.issuedAt().atZone(timeZone).toInstant(),
                parsed.totalAmount(),
                parsed.discountAmount(),
                parsed.approximateTaxes(),
                sourceUrl,
                fetched.sanitizedHtml());

        for (ParsedReceipt.Item item : parsed.items()) {
            long storeProductId = storeProducts.upsert(storeId, item.code(), item.description(), item.unit());
            StoreProduct storeProduct = storeProducts.getReferenceById(storeProductId);
            receipt.addItem(new ReceiptItem(storeProduct, item.lineNumber(), item.quantity(), item.unit(),
                    item.unitPrice(), item.totalPrice()));
        }
        for (ParsedReceipt.Payment payment : parsed.payments()) {
            receipt.addPayment(new ReceiptPayment(payment.method(), payment.amount()));
        }
        return receipts.saveAndFlush(receipt).getId();
    }
}
