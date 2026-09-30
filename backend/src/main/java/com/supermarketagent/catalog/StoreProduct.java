package com.supermarketagent.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A product as one store codes and describes it on its receipts. */
@Entity
@Table(name = "store_products")
public class StoreProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "store_code", nullable = false)
    private String storeCode;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String unit;

    /** Canonical product, filled by the product matching job (Epic 4). */
    @Column(name = "product_id")
    private Long productId;

    protected StoreProduct() {
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public Long getProductId() {
        return productId;
    }
}
