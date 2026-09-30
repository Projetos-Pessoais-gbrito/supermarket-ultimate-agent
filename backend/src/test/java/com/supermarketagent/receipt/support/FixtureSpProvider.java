package com.supermarketagent.receipt.support;

import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.provider.FetchedReceipt;
import com.supermarketagent.receipt.provider.NfceProvider;
import com.supermarketagent.receipt.provider.sp.SpNfcePageParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

/** SP provider that returns the anonymized fixture instead of calling SEFAZ. */
public final class FixtureSpProvider implements NfceProvider {

    public static final String KEY = "35260111222333000181650010000123451123456788";
    public static final String SP_URL = "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx";
    public static final String QR_URL = SP_URL + "?p=" + KEY + "|2|1|1|0123456789abcdef0123456789abcdef01234567";

    private final AtomicInteger calls = new AtomicInteger();
    private volatile RuntimeException failure;

    public int calls() {
        return calls.get();
    }

    /** Makes the next fetches throw, e.g. to simulate SEFAZ being down. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public String stateCode() {
        return "35";
    }

    @Override
    public ZoneId timeZone() {
        return ZoneId.of("America/Sao_Paulo");
    }

    @Override
    public FetchedReceipt fetch(AccessKey accessKey, String qrCodeUrl) {
        calls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        String html = fixture();
        return new FetchedReceipt(new SpNfcePageParser().parse(html), html);
    }

    public static String fixture() {
        try (InputStream in = FixtureSpProvider.class.getResourceAsStream("/fixtures/sp/nfce-10-items-credit-card.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
