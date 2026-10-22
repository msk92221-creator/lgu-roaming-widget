package com.msk.roamingwidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OfficialWebOriginTest {
    @Test public void officialExactRoamingPageOnly() {
        assertTrue(OfficialWebPage.isRoamingUrl(OfficialWebPage.URL));
        assertTrue(OfficialWebPage.isRoamingUrl(
                "https://m.lguplus.com/plan/roaming/my-roaming/"));
        assertTrue(OfficialWebPage.isRoamingUrl(
                "https://www.lguplus.com:443/plan/roaming/my-roaming"));
    }

    @Test public void lookalikeAndOtherOriginsAreRefused() {
        assertFalse(OfficialWebPage.isRoamingUrl(
                "https://www.lguplus.com.evil.invalid/plan/roaming/my-roaming"));
        assertFalse(OfficialWebPage.isRoamingUrl(
                "https://www.lguplus.com@evil.invalid/plan/roaming/my-roaming"));
        assertFalse(OfficialWebPage.isRoamingUrl(
                "http://www.lguplus.com/plan/roaming/my-roaming"));
        assertFalse(OfficialWebPage.isRoamingUrl(
                "https://www.lguplus.com:8443/plan/roaming/my-roaming"));
    }

    @Test public void otherPathsAndMalformedUrlsAreRefused() {
        assertFalse(OfficialWebPage.isRoamingUrl(null));
        assertFalse(OfficialWebPage.isRoamingUrl(""));
        assertFalse(OfficialWebPage.isRoamingUrl("https://www.lguplus.com/"));
        assertFalse(OfficialWebPage.isRoamingUrl(
                "https://www.lguplus.com/plan/roaming/my-roaming-other"));
        assertFalse(OfficialWebPage.isRoamingUrl("not a URL"));
    }
}
