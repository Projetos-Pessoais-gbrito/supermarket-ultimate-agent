package com.supermarketagent.receipt.provider;

import com.supermarketagent.receipt.domain.AccessKey;
import java.time.ZoneId;

/** Reads NFC-e data from one state's SEFAZ public consultation page. */
public interface NfceProvider {

    /** IBGE state code this provider handles, e.g. {@code "35"} for SP. */
    String stateCode();

    /** Time zone of the local issue times printed on this state's pages. */
    ZoneId timeZone();

    /**
     * Fetches and parses the receipt. Implementations must remove the buyer's personal data
     * from the returned HTML so it never reaches storage.
     *
     * @param accessKey the validated key extracted from {@code qrCodeUrl}
     * @param qrCodeUrl the full URL from the QR code (the public page requires its hash)
     */
    FetchedReceipt fetch(AccessKey accessKey, String qrCodeUrl);
}
