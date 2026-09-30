package com.supermarketagent.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(nullable = false)
    private String name;

    private String address;

    /** Name shoppers know, e.g. "ASSAI" for "SENDAS DISTRIBUIDORA S/A"; see {@link StoreNames}. */
    @Column(name = "display_name")
    private String displayName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    protected Store() {
    }

    public Long getId() {
        return id;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getName() {
        return name;
    }

    /** Brand or friendly name, falling back to the legal name for stores not backfilled yet. */
    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    public String getAddress() {
        return address;
    }

    public String getStateCode() {
        return stateCode;
    }
}
