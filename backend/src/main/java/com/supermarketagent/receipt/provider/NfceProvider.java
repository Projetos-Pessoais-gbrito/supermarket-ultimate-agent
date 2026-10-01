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

    /**
     * Rejects links this provider would never download (wrong host, scheme, port...), so malformed
     * links fail the same way whether or not the receipt was imported before.
     *
     * @throws UntrustedReceiptUrlException when the link is not an official consultation page
     */
    default void validateQrCodeUrl(String qrCodeUrl) {
    }

    /**
     * Reads a consultation page the user opened in the app after solving the SEFAZ captcha. The page
     * cannot be re-checked with SEFAZ, so implementations must at least remove personal data and
     * verify it shows {@code accessKey}.
     */
    default FetchedReceipt parseUserPage(AccessKey accessKey, String html) {
        throw new UnsupportedStateException(stateCode());
    }

    /** Public "consulta por chave" page for the key (the one protected by a captcha). */
    default String keyConsultationUrl(AccessKey accessKey) {
        throw new UnsupportedStateException(stateCode());
    }
}
