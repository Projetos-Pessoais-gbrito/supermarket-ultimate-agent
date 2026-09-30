package com.supermarketagent.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Canonical product shared by all stores, e.g. "ARROZ TIO JOAO TP1" 5000 g. */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    private String brand;

    private String category;

    @Column(name = "measure_value", precision = 12, scale = 4)
    private BigDecimal measureValue;

    /** {@code g} or {@code ml}; null when the description has no package size. */
    @Column(name = "measure_unit")
    private String measureUnit;

    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getMeasureValue() {
        return measureValue;
    }

    public String getMeasureUnit() {
        return measureUnit;
    }
}
