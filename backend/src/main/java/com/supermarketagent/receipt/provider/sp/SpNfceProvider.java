package com.supermarketagent.receipt.provider.sp;

import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.privacy.PersonalDataSanitizer;
import com.supermarketagent.receipt.provider.FetchedReceipt;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.NfceProvider;
import com.supermarketagent.receipt.provider.ParsedReceipt;
import com.supermarketagent.receipt.provider.RequestThrottle;
import com.supermarketagent.receipt.provider.SefazUnavailableException;
import com.supermarketagent.receipt.provider.UntrustedReceiptUrlException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Imports NFC-e from the SEFAZ-SP public QR code consultation page. */
@Component
public class SpNfceProvider implements NfceProvider {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static final int MAX_REDIRECTS = 3;

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
    public ZoneId timeZone() {
        return SAO_PAULO;
    }

    @Override
    public FetchedReceipt fetch(AccessKey accessKey, String qrCodeUrl) {
        String html = PersonalDataSanitizer.sanitizeHtml(download(trustedUri(qrCodeUrl)));
        ParsedReceipt receipt = parser.parse(html);
        if (!receipt.accessKey().equals(accessKey)) {
            throw new NfcePageParseException("SEFAZ returned a different receipt than the one requested");
        }
        return new FetchedReceipt(receipt, html);
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

    /**
     * Downloads the page, following redirects by hand: QR codes print a short link
     * ({@code /qrcode?p=...}) that SEFAZ-SP redirects to the consultation page. Every hop must
     * pass the same host allow-list, so a redirect can never lead outside SEFAZ-SP.
     */
    private String download(URI uri) {
        URI current = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            Response response = requestWithRetry(current);
            if (response.redirectTo() == null) {
                return response.body();
            }
            current = trustedUri(absolute(current, response.redirectTo()));
        }
        throw new NfcePageParseException("SEFAZ-SP redirected too many times");
    }

    private Response requestWithRetry(URI uri) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                throttle.acquire();
                return client.get().uri(uri).exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is3xxRedirection()) {
                        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                        if (location == null) {
                            throw new NfcePageParseException("SEFAZ-SP redirect without a location");
                        }
                        return new Response(null, location);
                    }
                    if (status.is5xxServerError()) {
                        throw new HttpServerErrorException(status);
                    }
                    if (status.is4xxClientError()) {
                        throw new HttpClientErrorException(status);
                    }
                    return new Response(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8), null);
                });
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

    /** Resolves a relative {@code Location} against the current page (kept as text: SEFAZ sends raw pipes). */
    private static String absolute(URI current, String location) {
        if (location.startsWith("/")) {
            return current.getScheme() + "://" + current.getHost() + location;
        }
        return location;
    }

    private record Response(String body, String redirectTo) {
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
