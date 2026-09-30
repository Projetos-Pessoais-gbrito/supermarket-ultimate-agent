package com.supermarketagent.receipt.provider.sp;

import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.NfceProvider;
import com.supermarketagent.receipt.provider.ParsedReceipt;
import com.supermarketagent.receipt.provider.RequestThrottle;
import com.supermarketagent.receipt.provider.SefazUnavailableException;
import com.supermarketagent.receipt.provider.UntrustedReceiptUrlException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Imports NFC-e from the SEFAZ-SP public QR code consultation page. */
@Component
public class SpNfceProvider implements NfceProvider {

    static final Set<String> TRUSTED_HOSTS = Set.of("www.nfce.fazenda.sp.gov.br", "nfce.fazenda.sp.gov.br");

    private final RestClient client;
    private final SpSefazProperties properties;
    private final RequestThrottle throttle;
    private final SpNfcePageParser parser = new SpNfcePageParser();

    public SpNfceProvider(@Qualifier("spSefazRestClient") RestClient client, SpSefazProperties properties) {
        this.client = client;
        this.properties = properties;
        this.throttle = new RequestThrottle(properties.minInterval());
    }

    @Override
    public String stateCode() {
        return "35";
    }

    @Override
    public ParsedReceipt fetch(AccessKey accessKey, String qrCodeUrl) {
        ParsedReceipt receipt = parser.parse(download(trustedUri(qrCodeUrl)));
        if (!receipt.accessKey().equals(accessKey)) {
            throw new NfcePageParseException("SEFAZ returned a different receipt than the one requested");
        }
        return receipt;
    }

    /** Accepts only SEFAZ-SP hosts and always uses HTTPS, whatever scheme the QR code printed. */
    static URI trustedUri(String qrCodeUrl) {
        URI parsed;
        try {
            parsed = new URI(qrCodeUrl.strip().replace("|", "%7C"));
        } catch (URISyntaxException | NullPointerException e) {
            throw new UntrustedReceiptUrlException("QR code URL is not a valid URL");
        }
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
        boolean defaultPort = parsed.getPort() == -1;
        if (!(scheme.equals("http") || scheme.equals("https")) || !TRUSTED_HOSTS.contains(host)
                || !defaultPort || parsed.getRawUserInfo() != null || parsed.getRawQuery() == null) {
            throw new UntrustedReceiptUrlException("QR code URL is not a SEFAZ-SP consultation page");
        }
        return URI.create("https://" + host + parsed.getRawPath() + "?" + parsed.getRawQuery());
    }

    private String download(URI uri) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                throttle.acquire();
                byte[] body = client.get().uri(uri).retrieve().body(byte[].class);
                if (body == null) {
                    throw new NfcePageParseException("SEFAZ returned an empty page");
                }
                return new String(body, StandardCharsets.UTF_8);
            } catch (HttpClientErrorException e) {
                throw new SefazUnavailableException("SEFAZ-SP rejected the request: " + e.getStatusCode(), e);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
                backOff(attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SefazUnavailableException("Interrupted while waiting to call SEFAZ-SP", e);
            }
        }
        throw new SefazUnavailableException(
                "SEFAZ-SP unavailable after " + properties.maxAttempts() + " attempts", lastFailure);
    }

    private void backOff(int attempt) {
        if (attempt == properties.maxAttempts()) {
            return;
        }
        try {
            Thread.sleep(properties.retryBackoff().multipliedBy(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SefazUnavailableException("Interrupted while waiting to retry SEFAZ-SP", e);
        }
    }
}
