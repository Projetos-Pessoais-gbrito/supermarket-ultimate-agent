package com.supermarketagent.receipt.persistence;

import com.supermarketagent.catalog.StoreProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "receipt_items")
public class ReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_product_id")
    private StoreProduct storeProduct;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    protected ReceiptItem() {
    }

    public ReceiptItem(StoreProduct storeProduct, int lineNumber, BigDecimal quantity, String unit,
                       BigDecimal unitPrice, BigDecimal totalPrice) {
        this.storeProduct = storeProduct;
        this.lineNumber = lineNumber;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    void attachTo(Receipt receipt) {
        this.receipt = receipt;
    }

    public Long getId() {
        return id;
    }

    public StoreProduct getStoreProduct() {
        return storeProduct;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
}
