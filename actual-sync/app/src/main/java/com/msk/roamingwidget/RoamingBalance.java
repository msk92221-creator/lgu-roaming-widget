package com.msk.roamingwidget;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Arithmetic uses only the two amounts displayed together by U+one. */
final class RoamingBalance {
    private static final Pattern AMOUNT = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB|KB|B)", Pattern.CASE_INSENSITIVE
    );

    final String remainingText;
    final String totalText;
    final String usedText;

    private RoamingBalance(String remaining, String total, String used) {
        remainingText = remaining;
        totalText = total;
        usedText = used;
    }

    static RoamingBalance fromDisplayedPair(String remainingText, String totalText) {
        Matcher remaining = AMOUNT.matcher(remainingText.replace(",", "").trim());
        Matcher total = AMOUNT.matcher(totalText.replace(",", "").trim());
        if (!remaining.matches() || !total.matches()
                || !remaining.group(2).equalsIgnoreCase(total.group(2))) {
            return null;
        }
        BigDecimal remainingValue = new BigDecimal(remaining.group(1));
        BigDecimal totalValue = new BigDecimal(total.group(1));
        if (totalValue.signum() <= 0 || remainingValue.compareTo(totalValue) > 0) {
            return null;
        }
        String unit = total.group(2).toUpperCase(Locale.ROOT);
        return new RoamingBalance(
                format(remainingValue, unit),
                format(totalValue, unit),
                format(totalValue.subtract(remainingValue), unit)
        );
    }

    String signature() {
        return remainingText + "/" + totalText;
    }

    private static String format(BigDecimal value, String unit) {
        return value.stripTrailingZeros().toPlainString() + " " + unit;
    }
}
