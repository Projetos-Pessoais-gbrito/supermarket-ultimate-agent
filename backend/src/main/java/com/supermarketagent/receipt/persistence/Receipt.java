package com.supermarketagent.receipt.persistence;

import com.supermarketagent.catalog.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "access_key", nullable = false, length = 44)
    private String accessKey;

    @Column(nullable = false)
    private Long number;

    @Column(nullable = false)
    private Integer series;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "approximate_taxes", nullable = false, precision = 12, scale = 2)
    private BigDecimal approximateTaxes;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "raw_html", nullable = false, columnDefinition = "text")
    private String rawHtml;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<ReceiptItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReceiptPayment> payments = new ArrayList<>();

    protected Receipt() {
    }

    public Receipt(Long userId, Store store, String accessKey, long number, int series, Instant issuedAt,
                   BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal approximateTaxes,
                   String sourceUrl, String rawHtml) {
        this.userId = userId;
        this.store = store;
        this.accessKey = accessKey;
        this.number = number;
        this.series = series;
        this.issuedAt = issuedAt;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.approximateTaxes = approximateTaxes;
        this.sourceUrl = sourceUrl;
        this.rawHtml = rawHtml;
    }

    public void addItem(ReceiptItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void addPayment(ReceiptPayment payment) {
        payment.attachTo(this);
        payments.add(payment);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Store getStore() {
        return store;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public Long getNumber() {
        return number;
    }

    public Integer getSeries() {
        return series;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getApproximateTaxes() {
        return approximateTaxes;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getRawHtml() {
        return rawHtml;
    }

    public List<ReceiptItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<ReceiptPayment> getPayments() {
        return Collections.unmodifiableList(payments);
    }
}
