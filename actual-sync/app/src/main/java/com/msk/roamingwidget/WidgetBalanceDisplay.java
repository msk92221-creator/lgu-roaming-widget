package com.msk.roamingwidget;

import java.math.BigDecimal;
import java.math.MathContext;

/** Presentation only: unknown balances must never look like a full allowance. */
final class WidgetBalanceDisplay {
    final String remainingLabel;
    final String usedLabel;
    final double remainingFraction;

    private WidgetBalanceDisplay(String remainingLabel, String usedLabel, double fraction) {
        this.remainingLabel = remainingLabel;
        this.usedLabel = usedLabel;
        remainingFraction = fraction;
    }

    static WidgetBalanceDisplay from(boolean hasValue, String remaining, String used, String total) {
        if (!hasValue) {
            return new WidgetBalanceDisplay("동기화 필요", "—", -1);
        }
        remaining = clean(remaining);
        used = clean(used);
        total = clean(total);
        if ("무제한".equals(remaining)) {
            return new WidgetBalanceDisplay("무제한", used.isEmpty() ? "—" : used, 1);
        }
        RoamingBalance pair = RoamingBalance.fromDisplayedPair(remaining, total);
        double fraction = -1;
        if (pair != null) {
            BigDecimal left = new BigDecimal(pair.remainingText.split(" ")[0]);
            BigDecimal whole = new BigDecimal(pair.totalText.split(" ")[0]);
            fraction = left.divide(whole, MathContext.DECIMAL64).doubleValue();
        }
        return new WidgetBalanceDisplay(
                remaining.isEmpty() ? "동기화 필요" : remaining + " 남음",
                used.isEmpty() ? "—" : used,
                fraction
        );
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
