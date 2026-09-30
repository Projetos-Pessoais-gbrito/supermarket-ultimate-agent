package com.supermarketagent.receipt.provider;

import com.supermarketagent.receipt.domain.AccessKey;

/** Reads NFC-e data from one state's SEFAZ public consultation page. */
public interface NfceProvider {

    /** IBGE state code this provider handles, e.g. {@code "35"} for SP. */
    String stateCode();

    /**
     * Fetches and parses the receipt.
     *
     * @param accessKey the validated key extracted from {@code qrCodeUrl}
     * @param qrCodeUrl the full URL from the QR code (the public page requires its hash)
     */
    ParsedReceipt fetch(AccessKey accessKey, String qrCodeUrl);
}
