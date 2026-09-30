package com.supermarketagent.catalog;

import com.supermarketagent.catalog.normalization.ProductNameNormalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The name people know a store by. SEFAZ pages show the company's legal name
 * ("SENDAS DISTRIBUIDORA S/A"), while shoppers know the brand ("ASSAI").
 */
public final class StoreNames {

    /** First 8 digits of the CNPJ identify the company across all its branches. */
    private static final Map<String, String> BRAND_BY_CNPJ_ROOT = Map.of(
            "06057223", "ASSAI"); // Sendas Distribuidora S/A

    /** Checked in order against the accent-free legal name. */
    private static final List<Map.Entry<Pattern, String>> BRAND_BY_LEGAL_NAME = List.of(
            Map.entry(Pattern.compile("\\bSENDAS\\b"), "ASSAI"),
            Map.entry(Pattern.compile("\\bATACADAO\\b"), "ATACADAO"),
            Map.entry(Pattern.compile("\\bCARREFOUR\\b"), "CARREFOUR"),
            Map.entry(Pattern.compile("\\bMAKRO\\b"), "MAKRO"),
            Map.entry(Pattern.compile("\\bSONDA\\b"), "SONDA"),
            Map.entry(Pattern.compile("\\bTENDA ATACADO\\b"), "TENDA ATACADO"),
            Map.entry(Pattern.compile("\\bROLDAO\\b"), "ROLDAO"));

    // Company type suffixes that add nothing for shoppers: "LTDA", "S/A", "S.A.", "EIRELI", "ME", "EPP"
    private static final Pattern COMPANY_SUFFIX =
            Pattern.compile("(\\s+(LTDA\\.?|S/A|S\\.A\\.?|SA|EIRELI|ME|EPP))+\\s*$");

    private StoreNames() {
    }

    public static String displayName(String cnpj, String legalName) {
        String digits = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (digits.length() >= 8 && BRAND_BY_CNPJ_ROOT.containsKey(digits.substring(0, 8))) {
            return BRAND_BY_CNPJ_ROOT.get(digits.substring(0, 8));
        }
        String upper = ProductNameNormalizer.upperWithoutAccents(legalName.strip());
        for (Map.Entry<Pattern, String> brand : BRAND_BY_LEGAL_NAME) {
            if (brand.getKey().matcher(upper).find()) {
                return brand.getValue();
            }
        }
        String withoutSuffix = COMPANY_SUFFIX.matcher(legalName.strip()).replaceAll("");
        return withoutSuffix.isBlank() ? legalName.strip() : withoutSuffix;
    }
}
