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

    @Test public void publicScriptReferencesExcludeSecretsAndForeignOrigins() {
        assertTrue(OfficialWebPage.isPublicScriptReference(
                "https://m.lguplus.com/static/uhdc-mbfo/build/_nuxt/app_abc.js"));
        assertTrue(OfficialWebPage.isPublicScriptReference(
                "https://www.lguplus.com/_next/static/chunks/plan-roaming-page.js"));
        for (String value : new String[]{null, "https://m.lguplus.com/static/app.js?token=secret",
                "https://m.lguplus.com/static/app.js#secret",
                "https://evil.invalid/static/app.js", "https://user:pass@m.lguplus.com/static/app.js",
                "http://m.lguplus.com/static/app.js", "https://m.lguplus.com/api/profile.js",
                "https://m.lguplus.com/static/%61pp.js",
                "https://m.lguplus.com/static/../api/profile.js"}) {
            assertFalse(String.valueOf(value), OfficialWebPage.isPublicScriptReference(value));
        }
    }

    @Test public void staleNavigationFailureCannotMatchANewPage() {
        assertTrue(OfficialWebPage.sameNavigation(OfficialWebPage.URL + "#meter", OfficialWebPage.URL));
        assertFalse(OfficialWebPage.sameNavigation(OfficialWebPage.URL,
                "https://account.lguplus.com/login"));
        assertFalse(OfficialWebPage.sameNavigation(OfficialWebPage.URL + "?request=old",
                OfficialWebPage.URL + "?request=new"));
        assertFalse(OfficialWebPage.sameNavigation(null, OfficialWebPage.URL));
    }
}
