package com.msk.roamingwidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WebRefreshCaptureStabilityTest {
    @Test
    public void requiresTwoConsecutiveFreshIdenticalReads() {
        WebRefreshJobService.CaptureStability capture = new WebRefreshJobService.CaptureStability();
        assertFalse(capture.observe(ready("44GB"), true));
        assertTrue(capture.observe(ready("44.0GB"), true));
    }

    @Test
    public void cachedOrUnfinishedPageNeverQualifies() {
        WebRefreshJobService.CaptureStability capture = new WebRefreshJobService.CaptureStability();
        assertFalse(capture.observe(ready("44GB"), false));
        assertFalse(capture.observe(ready("44GB"), false));
        assertFalse(capture.observe(ready("44GB"), true));
        assertTrue(capture.observe(ready("44GB"), true));
    }

    @Test
    public void changingBalanceStartsNewStabilityWindow() {
        WebRefreshJobService.CaptureStability capture = new WebRefreshJobService.CaptureStability();
        assertFalse(capture.observe(ready("44GB"), true));
        assertFalse(capture.observe(ready("43.9GB"), true));
        assertTrue(capture.observe(ready("43.9GB"), true));
    }

    @Test
    public void loadingAndInvalidResultsBreakConsecutiveReads() {
        WebRefreshJobService.CaptureStability capture = new WebRefreshJobService.CaptureStability();
        assertFalse(capture.observe(ready("44GB"), true));
        assertFalse(capture.observe(OfficialWebBalanceParser.parse("loading", "44GB", "49GB"), true));
        assertFalse(capture.observe(ready("44GB"), true));
        assertFalse(capture.observe(null, true));
        assertFalse(capture.observe(ready("44GB"), true));
        assertTrue(capture.observe(ready("44GB"), true));
    }

    @Test
    public void resetPreventsCrossRequestStability() {
        WebRefreshJobService.CaptureStability capture = new WebRefreshJobService.CaptureStability();
        assertFalse(capture.observe(ready("44GB"), true));
        capture.reset();
        assertFalse(capture.observe(ready("44GB"), true));
        assertTrue(capture.observe(ready("44GB"), true));
    }

    private static UplusScreenParser.Result ready(String remaining) {
        return OfficialWebBalanceParser.parse("ready", remaining, "49GB");
    }
}
