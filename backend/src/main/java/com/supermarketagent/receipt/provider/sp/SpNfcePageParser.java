package com.supermarketagent.receipt.provider.sp;

import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.domain.InvalidAccessKeyException;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.ParsedReceipt;
import com.supermarketagent.receipt.provider.ParsedReceipt.Item;
import com.supermarketagent.receipt.provider.ParsedReceipt.Payment;
import com.supermarketagent.receipt.provider.ParsedReceipt.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Parses the SEFAZ-SP NFC-e public consultation page ("Consulta Resumida", XSLT 2.05). */
public class SpNfcePageParser {

    private static final Pattern ISSUE_INFO = Pattern.compile(
            "Número:\\s*(\\d+)\\s*Série:\\s*(\\d+)\\s*Emissão:\\s*(\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2})");
    private static final Pattern ITEM_CODE = Pattern.compile("Código:\\s*([^\\s)]+)");
    private static final DateTimeFormatter ISSUE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public ParsedReceipt parse(String html) {
        Document page = Jsoup.parse(html);
        if (page.getElementById("hdfNotaCancelada") != null || page.getElementById("hdfNotaDenegada") != null) {
            throw new NfcePageParseException("Receipt was cancelled or denied by SEFAZ");
        }
        Element items = page.getElementById("tabResult");
        if (items == null) {
            throw new NfcePageParseException("Page does not contain an NFC-e (items table not found)");
        }

        Matcher issueInfo = ISSUE_INFO.matcher(text(page.selectFirst("#infos li")));
        if (!issueInfo.find()) {
            throw new NfcePageParseException("Receipt number, series and issue date not found");
        }

        Totals totals = parseTotals(page);
        return new ParsedReceipt(
                parseAccessKey(page),
                parseStore(page),
                Long.parseLong(issueInfo.group(1)),
                Integer.parseInt(issueInfo.group(2)),
                parseIssueDate(issueInfo.group(3)),
                parseItems(items.select("tr[id^=Item]")),
                totals.amountToPay,
                totals.discount,
                totals.taxes,
                totals.payments);
    }

    private static AccessKey parseAccessKey(Document page) {
        try {
            return AccessKey.parse(text(page.selectFirst("span.chave")));
        } catch (InvalidAccessKeyException e) {
            throw new NfcePageParseException("Access key not found on page", e);
        }
    }

    private static Store parseStore(Document page) {
        Elements lines = page.select("div.txtCenter > div.text");
        if (lines.size() < 2) {
            throw new NfcePageParseException("Store CNPJ and address not found");
        }
        String cnpj = lines.get(0).text().replaceAll("\\D", "");
        // Address parts are comma separated with empty slots for missing fields (e.g. complement)
        String address = Arrays.stream(lines.get(1).text().split(","))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(", "));
        return new Store(cnpj, text(page.getElementById("u20")), address);
    }

    private static List<Item> parseItems(Elements rows) {
        if (rows.isEmpty()) {
            throw new NfcePageParseException("Receipt has no items");
        }
        List<Item> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            items.add(new Item(
                    i + 1,
                    itemCode(ownText(row, "span.RCod")),
                    ownText(row, "span.txtTit"),
                    decimal(ownText(row, "span.Rqtd")),
                    ownText(row, "span.RUN"),
                    decimal(ownText(row, "span.RvlUnit")),
                    decimal(ownText(row, "span.valor"))));
        }
        return items;
    }

    private static Totals parseTotals(Document page) {
        Totals totals = new Totals();
        boolean inPayments = false;
        for (Element line : page.select("#totalNota > div")) {
            String label = text(line.selectFirst("label"));
            String value = text(line.selectFirst("span.totalNumb"));
            if (label.startsWith("Forma de pagamento")) {
                inPayments = true;
            } else if (label.startsWith("Troco")) {
                inPayments = false;
            } else if (label.startsWith("Valor a pagar")) {
                totals.amountToPay = decimal(value);
            } else if (label.startsWith("Descontos")) {
                totals.discount = decimal(value);
            } else if (label.contains("Tributos")) {
                totals.taxes = decimal(value);
            } else if (inPayments && !label.isEmpty()) {
                totals.payments.add(new Payment(label, decimal(value)));
            }
        }
        if (totals.amountToPay == null) {
            throw new NfcePageParseException("Total amount not found");
        }
        return totals;
    }

    private static LocalDateTime parseIssueDate(String value) {
        try {
            return LocalDateTime.parse(value, ISSUE_DATE);
        } catch (DateTimeParseException e) {
            throw new NfcePageParseException("Invalid issue date: " + value, e);
        }
    }

    private static String itemCode(String value) {
        Matcher matcher = ITEM_CODE.matcher(value);
        if (!matcher.find()) {
            throw new NfcePageParseException("Invalid item code: " + value);
        }
        return matcher.group(1);
    }

    /** Parses Brazilian formatted numbers such as {@code 1.234,56}. */
    static BigDecimal decimal(String value) {
        String normalized = value.replace(".", "").replace(',', '.').strip();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new NfcePageParseException("Invalid number: " + value, e);
        }
    }

    private static String ownText(Element row, String selector) {
        Element element = row.selectFirst(selector);
        if (element == null) {
            throw new NfcePageParseException("Item field not found: " + selector);
        }
        return element.ownText().strip();
    }

    private static String text(Element element) {
        return element == null ? "" : element.text().strip();
    }

    private static final class Totals {
        BigDecimal amountToPay;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        final List<Payment> payments = new ArrayList<>();
    }
}
