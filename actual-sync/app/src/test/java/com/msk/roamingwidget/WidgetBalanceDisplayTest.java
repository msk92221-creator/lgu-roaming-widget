package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class WidgetBalanceDisplayTest {
    @Test
    public void remainingFractionComesFromOfficialBalance() {
        WidgetBalanceDisplay display = WidgetBalanceDisplay.from(true, "47.8 GB", "1.2 GB", "49 GB");
        assertEquals(47.8 / 49, display.remainingFraction, 0.000001);
        assertEquals("47.8 GB 남음", display.remainingLabel);
        assertEquals("1.2 GB", display.usedLabel);
    }

    @Test
    public void missingOrInvalidTotalCannotAppearAsFullBar() {
        assertEquals(-1, WidgetBalanceDisplay.from(false, "47.8 GB", "1.2 GB", "49 GB").remainingFraction, 0);
        assertEquals(-1, WidgetBalanceDisplay.from(true, "47.8 GB", "", "").remainingFraction, 0);
        assertEquals(-1, WidgetBalanceDisplay.from(true, "50 GB", "", "49 GB").remainingFraction, 0);
        assertEquals(-1, WidgetBalanceDisplay.from(true, "500 MB", "", "49 GB").remainingFraction, 0);
    }

    @Test
    public void zeroAndExplicitUnlimitedAreRepresentedCorrectly() {
        assertEquals(0, WidgetBalanceDisplay.from(true, "0 GB", "49 GB", "49 GB").remainingFraction, 0);
        WidgetBalanceDisplay unlimited = WidgetBalanceDisplay.from(true, "무제한", "", "");
        assertEquals(1, unlimited.remainingFraction, 0);
        assertEquals("무제한", unlimited.remainingLabel);
    }
}
