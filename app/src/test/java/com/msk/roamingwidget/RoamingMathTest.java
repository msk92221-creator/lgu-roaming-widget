package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RoamingMathTest {
    @Test
    public void remainingSubtractsOnlyNewUsage() {
        assertEquals(7_500L, RoamingMath.calculateRemaining(8_000L, 10_000L, 10_500L));
    }

    @Test
    public void counterResetKeepsCurrentRemaining() {
        assertEquals(7_500L, RoamingMath.calculateRemaining(7_500L, 10_500L, 3_000L));
    }

    @Test
    public void usageAfterResetIsCountedFromNewCounter() {
        long afterReset = RoamingMath.calculateRemaining(7_500L, 10_500L, 3_000L);
        assertEquals(7_000L, RoamingMath.calculateRemaining(afterReset, 3_000L, 3_500L));
    }

    @Test
    public void remainingNeverDropsBelowZero() {
        assertEquals(0L, RoamingMath.calculateRemaining(1_000L, 10_000L, 20_000L));
    }

    @Test
    public void progressIsClamped() {
        assertEquals(0, RoamingMath.remainingPermille(10_000L, -1L));
        assertEquals(500, RoamingMath.remainingPermille(10_000L, 5_000L));
        assertEquals(1000, RoamingMath.remainingPermille(10_000L, 20_000L));
    }
}
