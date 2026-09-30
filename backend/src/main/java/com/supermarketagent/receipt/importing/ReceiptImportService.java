package com.supermarketagent.receipt.importing;

import com.supermarketagent.catalog.ProductMatcher;
import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.domain.InvalidAccessKeyException;
import com.supermarketagent.receipt.domain.KeyOnlyLinkException;
import com.supermarketagent.receipt.domain.QrCodeUrlParser;
import com.supermarketagent.receipt.persistence.ReceiptRepository;
import com.supermarketagent.receipt.persistence.ReceiptSource;
import com.supermarketagent.receipt.privacy.PersonalDataSanitizer;
import com.supermarketagent.receipt.provider.FetchedReceipt;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.NfceProvider;
import com.supermarketagent.receipt.provider.NfceProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Imports a receipt from its QR code: validate → skip if already imported → fetch from SEFAZ → store.
 *
 * <p>The SEFAZ call happens outside any database transaction so a slow SEFAZ never holds a connection.
 */
@Service
public class ReceiptImportService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptImportService.class);

    private final NfceProviderRegistry providers;
    private final ReceiptRepository receipts;
    private final ReceiptWriter writer;
    private final ProductMatcher productMatcher;

    ReceiptImportService(NfceProviderRegistry providers, ReceiptRepository receipts, ReceiptWriter writer,
                         ProductMatcher productMatcher) {
        this.providers = providers;
        this.receipts = receipts;
        this.writer = writer;
        this.productMatcher = productMatcher;
    }

    public ReceiptImportResult importFromQrCode(long userId, String qrCodeUrl) {
        AccessKey accessKey = QrCodeUrlParser.extractAccessKey(qrCodeUrl);
        if (!accessKey.isNfce()) {
            throw new InvalidAccessKeyException("Only NFC-e (consumer receipt, model 65) can be imported");
        }
        // Checked before the "already imported" shortcut too, so the answer does not depend on history
        if (QrCodeUrlParser.isKeyOnlyLink(qrCodeUrl)) {
            throw new KeyOnlyLinkException();
        }

        var existing = receipts.findIdByUserIdAndAccessKey(userId, accessKey.value());
        if (existing.isPresent()) {
            return new ReceiptImportResult(existing.get(), false);
        }

        NfceProvider provider = providers.providerFor(accessKey);
        FetchedReceipt fetched = provider.fetch(accessKey, qrCodeUrl);
        String sourceUrl = PersonalDataSanitizer.sanitizeQrCodeUrl(qrCodeUrl.strip());
        return save(userId, accessKey, fetched, provider, sourceUrl, ReceiptSource.QR_CODE);
    }

    /**
     * Imports the consultation page the user opened in the app after solving the SEFAZ captcha.
     * SEFAZ cannot be asked again, so the page must show the requested key and the store must be the
     * issuer encoded in that key; it may add new stores/products but never rename shared ones.
     */
    public ReceiptImportResult importFromCaptchaPage(long userId, String accessKeyValue, String html) {
        AccessKey accessKey = AccessKey.parse(accessKeyValue);
        if (!accessKey.isNfce()) {
            throw new InvalidAccessKeyException("Only NFC-e (consumer receipt, model 65) can be imported");
        }
        var existing = receipts.findIdByUserIdAndAccessKey(userId, accessKey.value());
        if (existing.isPresent()) {
            return new ReceiptImportResult(existing.get(), false);
        }

        NfceProvider provider = providers.providerFor(accessKey);
        FetchedReceipt fetched = provider.parseUserPage(accessKey, html);
        if (!fetched.receipt().store().cnpj().equals(accessKey.issuerCnpj())) {
            throw new NfcePageParseException("The store on the page is not the issuer of this access key");
        }
        return save(userId, accessKey, fetched, provider, provider.keyConsultationUrl(accessKey),
                ReceiptSource.CAPTCHA_PAGE);
    }

    private ReceiptImportResult save(long userId, AccessKey accessKey, FetchedReceipt fetched, NfceProvider provider,
                                     String sourceUrl, ReceiptSource source) {
        long receiptId;
        try {
            receiptId = writer.save(userId, fetched, provider.timeZone(), sourceUrl, source);
        } catch (DataIntegrityViolationException e) {
            // The same user imported this receipt concurrently; the other request won
            return receipts.findIdByUserIdAndAccessKey(userId, accessKey.value())
                    .map(id -> new ReceiptImportResult(id, false))
                    .orElseThrow(() -> e);
        }
        linkProducts();
        return new ReceiptImportResult(receiptId, true);
    }

    /** Best effort: the scheduled job links anything left behind, so a failure must not fail the import. */
    private void linkProducts() {
        try {
            productMatcher.linkUnlinkedStoreProducts();
        } catch (RuntimeException e) {
            log.warn("Product matching after import failed; it will be retried in background", e);
        }
    }
}
