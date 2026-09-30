package com.supermarketagent.catalog.normalization;

import com.supermarketagent.catalog.normalization.Measure.BaseUnit;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes receipt descriptions such as {@code "Flocão de Milho da Terrinha 400g"} into a comparable
 * name ({@code "FLOCAO DE MILHO DA TERRINHA"}) and package size (400 g).
 *
 * <p>Only sizes with a unit count: a bare {@code KG} means "sold by weight" and {@code 48X55} is a
 * dimension, so neither is a package size.
 */
public final class ProductNameNormalizer {

    private static final String NUMBER = "(\\d+(?:[.,]\\d+)?)";
    private static final String UNIT = "(KG|KGS|G|GR|GRS|L|LT|LTS|LTR|ML)";
    // "6X1L", "12 X 350ML": pack count times unit size
    private static final Pattern MULTIPACK = Pattern.compile("\\b(\\d+)\\s?X\\s?" + NUMBER + "\\s?" + UNIT + "\\b");
    private static final Pattern SIZE = Pattern.compile("\\b" + NUMBER + "\\s?" + UNIT + "\\b");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_NAME_CHARACTER = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ProductNameNormalizer() {
    }

    public static NormalizedProduct normalize(String description) {
        String text = upperWithoutAccents(description);

        Optional<Measure> measure = Optional.empty();
        Matcher multipack = MULTIPACK.matcher(text);
        if (multipack.find()) {
            BigDecimal count = new BigDecimal(multipack.group(1));
            measure = Optional.of(toBaseUnit(number(multipack.group(2)).multiply(count), multipack.group(3)));
            text = remove(text, multipack);
        } else {
            Matcher size = SIZE.matcher(text);
            if (size.find()) {
                measure = Optional.of(toBaseUnit(number(size.group(1)), size.group(2)));
                text = remove(text, size);
            }
        }

        String name = SPACES.matcher(NOT_NAME_CHARACTER.matcher(text).replaceAll(" ")).replaceAll(" ").strip();
        return new NormalizedProduct(name, measure);
    }

    public static String upperWithoutAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toUpperCase(Locale.ROOT);
    }

    private static Measure toBaseUnit(BigDecimal amount, String unit) {
        return switch (unit) {
            case "KG", "KGS" -> new Measure(amount.movePointRight(3), BaseUnit.GRAM);
            case "G", "GR", "GRS" -> new Measure(amount, BaseUnit.GRAM);
            case "L", "LT", "LTS", "LTR" -> new Measure(amount.movePointRight(3), BaseUnit.MILLILITER);
            case "ML" -> new Measure(amount, BaseUnit.MILLILITER);
            default -> throw new IllegalArgumentException("Unknown unit " + unit);
        };
    }

    private static BigDecimal number(String value) {
        return new BigDecimal(value.replace(',', '.'));
    }

    private static String remove(String text, Matcher match) {
        return text.substring(0, match.start()) + " " + text.substring(match.end());
    }
}
