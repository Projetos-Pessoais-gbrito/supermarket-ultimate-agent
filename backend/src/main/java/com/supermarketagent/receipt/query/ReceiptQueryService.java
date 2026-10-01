package com.supermarketagent.receipt.query;

import com.supermarketagent.catalog.Product;
import com.supermarketagent.catalog.ProductCategory;
import com.supermarketagent.catalog.ProductRepository;
import com.supermarketagent.catalog.Store;
import com.supermarketagent.catalog.StoreProduct;
import com.supermarketagent.receipt.persistence.Receipt;
import com.supermarketagent.receipt.persistence.ReceiptRepository;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for receipts; every query is scoped to the owner. */
@Service
@Transactional(readOnly = true)
public class ReceiptQueryService {

    private final ReceiptRepository receipts;
    private final ProductRepository products;

    ReceiptQueryService(ReceiptRepository receipts, ProductRepository products) {
        this.receipts = receipts;
        this.products = products;
    }

    public ReceiptDetails details(long userId, long receiptId) {
        Receipt receipt = receipts.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new ReceiptNotFoundException(receiptId));
        Store store = receipt.getStore();
        // One query for the canonical products of all items (friendly names and categories)
        Map<Long, Product> productsById = products.findAllById(receipt.getItems().stream()
                        .map(item -> item.getStoreProduct().getProductId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
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
                        .map(item -> {
                            StoreProduct storeProduct = item.getStoreProduct();
                            Product product = productsById.get(storeProduct.getProductId());
                            return new ReceiptDetails.Item(
                                    item.getLineNumber(),
                                    storeProduct.getStoreCode(),
                                    storeProduct.getDescription(),
                                    item.getQuantity(),
                                    item.getUnit(),
                                    item.getUnitPrice(),
                                    item.getTotalPrice(),
                                    product == null ? null : product.getId(),
                                    product == null ? null : product.getFriendlyName(),
                                    product == null || product.getCategory() == null
                                            ? null
                                            : ProductCategory.valueOf(product.getCategory()).label());
                        })
                        .toList(),
                receipt.getPayments().stream()
                        .map(payment -> new ReceiptDetails.Payment(payment.getMethod(), payment.getAmount()))
                        .toList());
    }
}
