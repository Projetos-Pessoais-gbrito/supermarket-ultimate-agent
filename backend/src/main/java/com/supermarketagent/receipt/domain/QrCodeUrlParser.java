package com.supermarketagent.receipt.domain;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Extracts the access key from the URL encoded in an NFC-e QR code.
 *
 * <p>Supported layouts:
 * <ul>
 *   <li>v2/v3: {@code ?p=<key>|<version>|<env>|...} (online and offline emission)</li>
 *   <li>v1: {@code ?chNFe=<key>&nVersao=100&...}</li>
 * </ul>
 */
public final class QrCodeUrlParser {

    private QrCodeUrlParser() {
    }

    public static AccessKey extractAccessKey(String url) {
        String query = rawQuery(url);
        String key = queryParam(query, "p")
                .map(p -> p.split("\\|", 2)[0])
                .or(() -> queryParam(query, "chNFe"))
                .orElseThrow(() -> new InvalidAccessKeyException("QR code URL does not contain an access key"));
        return AccessKey.parse(key);
    }

    private static String rawQuery(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidAccessKeyException("QR code URL must not be empty");
        }
        try {
            // Pipes are not legal in a URI, but QR codes print them unencoded
            String query = URI.create(url.strip().replace("|", "%7C")).getRawQuery();
            if (query == null) {
                throw new InvalidAccessKeyException("QR code URL has no query string");
            }
            return query;
        } catch (IllegalArgumentException e) {
            throw new InvalidAccessKeyException("QR code URL is not a valid URL");
        }
    }

    private static Optional<String> queryParam(String rawQuery, String name) {
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(name)) {
                return Optional.of(URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
    }
}
