package com.supermarketagent.receipt.importing;

import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.domain.InvalidAccessKeyException;
import com.supermarketagent.receipt.domain.QrCodeUrlParser;
import com.supermarketagent.receipt.persistence.ReceiptRepository;
import com.supermarketagent.receipt.privacy.PersonalDataSanitizer;
import com.supermarketagent.receipt.provider.FetchedReceipt;
import com.supermarketagent.receipt.provider.NfceProvider;
import com.supermarketagent.receipt.provider.NfceProviderRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Imports a receipt from its QR code: validate → skip if already imported → fetch from SEFAZ → store.
 *
 * <p>The SEFAZ call happens outside any database transaction so a slow SEFAZ never holds a connection.
 */
@Service
public class ReceiptImportService {

    private final NfceProviderRegistry providers;
    private final ReceiptRepository receipts;
    private final ReceiptWriter writer;

    ReceiptImportService(NfceProviderRegistry providers, ReceiptRepository receipts, ReceiptWriter writer) {
        this.providers = providers;
        this.receipts = receipts;
        this.writer = writer;
    }

    public ReceiptImportResult importFromQrCode(long userId, String qrCodeUrl) {
        AccessKey accessKey = QrCodeUrlParser.extractAccessKey(qrCodeUrl);
        if (!accessKey.isNfce()) {
            throw new InvalidAccessKeyException("Only NFC-e (consumer receipt, model 65) can be imported");
        }

        var existing = receipts.findIdByUserIdAndAccessKey(userId, accessKey.value());
        if (existing.isPresent()) {
            return new ReceiptImportResult(existing.get(), false);
        }

        NfceProvider provider = providers.providerFor(accessKey);
        FetchedReceipt fetched = provider.fetch(accessKey, qrCodeUrl);
        String sourceUrl = PersonalDataSanitizer.sanitizeQrCodeUrl(qrCodeUrl.strip());
        try {
            return new ReceiptImportResult(writer.save(userId, fetched, provider.timeZone(), sourceUrl), true);
        } catch (DataIntegrityViolationException e) {
            // The same user imported this receipt concurrently; the other request won
            return receipts.findIdByUserIdAndAccessKey(userId, accessKey.value())
                    .map(id -> new ReceiptImportResult(id, false))
                    .orElseThrow(() -> e);
        }
    }
}
