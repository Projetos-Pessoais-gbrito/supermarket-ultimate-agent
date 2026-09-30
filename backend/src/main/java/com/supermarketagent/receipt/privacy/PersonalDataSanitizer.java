package com.supermarketagent.receipt.privacy;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Removes the buyer's personal data (CPF, name, address) from SEFAZ pages and QR code URLs
 * before they are stored (LGPD). Receipts only need the store and items.
 */
public final class PersonalDataSanitizer {

    static final String REMOVED = "Dados do consumidor removidos";

    // 123.456.789-09 anywhere, or "CPF: 12345678909" when printed without punctuation
    private static final Pattern FORMATTED_CPF = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");
    private static final Pattern LABELLED_CPF = Pattern.compile("(?i)(CPF\\W{0,5})\\d{11}\\b");

    private PersonalDataSanitizer() {
    }

    public static String sanitizeHtml(String html) {
        Document page = Jsoup.parse(html);
        for (Element heading : page.select("h4")) {
            if (heading.text().strip().equalsIgnoreCase("Consumidor")) {
                Element section = heading.parent();
                for (Element child : section.children()) {
                    if (child != heading) {
                        child.remove();
                    }
                }
                section.appendElement("ul").appendElement("li").text(REMOVED);
            }
        }
        // ASP.NET view state is an encoded copy of the page data
        page.select("input#__VIEWSTATE, input#__EVENTVALIDATION").attr("value", "");

        String sanitized = FORMATTED_CPF.matcher(page.outerHtml()).replaceAll("***.***.***-**");
        return LABELLED_CPF.matcher(sanitized).replaceAll("$1***********");
    }

    /** Drops the {@code cDest} (buyer CPF/CNPJ) parameter used by v1 QR codes. */
    public static String sanitizeQrCodeUrl(String url) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return url;
        }
        String query = Arrays.stream(url.substring(queryStart + 1).split("&"))
                .filter(param -> !param.toLowerCase(Locale.ROOT).startsWith("cdest="))
                .collect(Collectors.joining("&"));
        return url.substring(0, queryStart + 1) + query;
    }
}
