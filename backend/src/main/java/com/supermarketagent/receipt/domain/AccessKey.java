package com.supermarketagent.receipt.domain;

import java.time.YearMonth;
import java.util.Objects;

/**
 * The 44-digit access key ("chave de acesso") that identifies an NF-e / NFC-e.
 *
 * <pre>
 * cUF(2) AAMM(4) CNPJ(14) model(2) series(3) number(9) tpEmis(1) cNF(8) cDV(1)
 * </pre>
 */
public record AccessKey(String value) {

    public static final int LENGTH = 44;
    public static final String MODEL_NFCE = "65";

    public AccessKey {
        Objects.requireNonNull(value, "access key must not be null");
        if (!value.matches("\\d{" + LENGTH + "}")) {
            throw new InvalidAccessKeyException("Access key must have exactly 44 digits");
        }
    }

    /** Parses a key as printed on receipts, ignoring spaces and other separators. */
    public static AccessKey parse(String raw) {
        if (raw == null) {
            throw new InvalidAccessKeyException("Access key must not be empty");
        }
        return new AccessKey(raw.replaceAll("\\D", ""));
    }

    public String stateCode() {
        return value.substring(0, 2);
    }

    public YearMonth issueYearMonth() {
        int year = 2000 + Integer.parseInt(value.substring(2, 4));
        int month = Integer.parseInt(value.substring(4, 6));
        return YearMonth.of(year, month);
    }

    public String issuerCnpj() {
        return value.substring(6, 20);
    }

    public String model() {
        return value.substring(20, 22);
    }

    public int series() {
        return Integer.parseInt(value.substring(22, 25));
    }

    public long number() {
        return Long.parseLong(value.substring(25, 34));
    }

    public char emissionType() {
        return value.charAt(34);
    }

    public int checkDigit() {
        return Character.getNumericValue(value.charAt(43));
    }

    public boolean isNfce() {
        return MODEL_NFCE.equals(model());
    }

    @Override
    public String toString() {
        return value;
    }
}
