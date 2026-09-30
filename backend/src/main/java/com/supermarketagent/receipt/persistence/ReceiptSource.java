package com.supermarketagent.receipt.persistence;

/** How a receipt reached the backend. */
public enum ReceiptSource {
    /** Downloaded by the backend from the SEFAZ page the QR code points to. */
    QR_CODE,
    /** Page sent by the app after the user solved the SEFAZ captcha; not re-checked with SEFAZ. */
    CAPTCHA_PAGE;

    /** Only receipts the backend downloaded itself may refresh shared store and product names. */
    public boolean updatesSharedCatalog() {
        return this == QR_CODE;
    }
}
