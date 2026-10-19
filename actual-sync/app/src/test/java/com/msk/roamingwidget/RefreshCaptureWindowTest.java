package com.msk.roamingwidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class RefreshCaptureWindowTest {
    @Test
    public void frequentIdenticalScansDoNotResetTheStabilityClock() {
        RefreshCaptureWindow window = new RefreshCaptureWindow();
        assertFalse(window.observe("A", 0));
        assertFalse(window.observe("A", 100));
        assertFalse(window.observe("A", 500));
        assertTrue(window.observe("A", 650));
        assertFalse(window.observe("A", 1000));
    }

    @Test
    public void laterUpdatedPageValueIsSavedWithinObservationWindow() {
        RefreshCaptureWindow window = new RefreshCaptureWindow();
        assertFalse(window.observe("cached", 0));
        assertTrue(window.observe("cached", 750));
        assertFalse(window.observe("fresh", 3000));
        assertTrue(window.observe("fresh", 3750));
        assertFalse(window.observe("fresh", 4500));
        assertFalse(window.isComplete(10_749));
        assertTrue(window.isComplete(10_750));
        assertFalse(window.observe("fresh", 10_750));
    }

    @Test
    public void changedValuesAndLoadingRequireStabilityAgain() {
        RefreshCaptureWindow window = new RefreshCaptureWindow();
        assertFalse(window.observe("A", 0));
        assertFalse(window.observe("B", 500));
        assertFalse(window.observe("B", 1000));
        window.resetCandidate();
        assertFalse(window.observe("B", 1100));
        assertTrue(window.observe("B", 1800));
    }

    @Test
    public void failedScansNeverFinishARequestAsSuccessful() {
        RefreshCaptureWindow window = new RefreshCaptureWindow();
        assertFalse(window.isComplete(100_000));
        window.resetCandidate();
        assertFalse(window.isComplete(200_000));
    }

    @Test
    public void returningToAnAlreadySavedValueMustStabilizeWithoutResaving() {
        RefreshCaptureWindow window = new RefreshCaptureWindow();
        window.observe("A", 0);
        assertTrue(window.observe("A", 750));
        window.resetCandidate();
        assertFalse(window.hasStableSavedValue("A", 1000));
        assertFalse(window.observe("A", 1000));
        assertFalse(window.hasStableSavedValue("A", 1500));
        assertFalse(window.observe("A", 1750));
        assertTrue(window.hasStableSavedValue("A", 1750));
    }
}
