package com.msk.roamingwidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/** Structural regression checks for the user's no-foreground-Activity requirement. */
public final class SilentRefreshWiringTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/msk/roamingwidget",
                name + ".java")), StandardCharsets.UTF_8);
    }

    @Test public void widgetUsesAnExplicitRefreshBroadcast() throws Exception {
        String widget = source("WidgetUpdater");
        assertTrue(widget.contains("PendingIntent.getBroadcast("));
        assertTrue(widget.contains("RoamingWidgetProvider.ACTION_REFRESH"));
        assertFalse(widget.contains("PendingIntent.getActivity("));
    }

    @Test public void refreshWorkerAndReceiverNeverLaunchActivities() throws Exception {
        for (String name : new String[]{"BackgroundRefresh", "WebRefreshJobService",
                "RoamingWidgetProvider"}) {
            String text = source(name);
            assertFalse(name, text.contains("startActivity("));
            assertFalse(name, text.contains("getActivity("));
            assertFalse(name, text.contains("UplusLauncher"));
            assertFalse(name, text.contains("SyncActivity"));
        }
        assertTrue(source("RoamingWidgetProvider").contains("BackgroundRefresh.request(context)"));
    }

    @Test public void legacyForegroundCaptureIsNotRegistered() throws Exception {
        String manifest = new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8);
        assertFalse(manifest.contains("UplusAccessibilityService"));
        assertFalse(manifest.contains("SyncActivity"));
        assertFalse(manifest.contains("BIND_ACCESSIBILITY_SERVICE"));
        assertFalse(manifest.contains("com.lguplus.mobile.cs"));
        assertTrue(manifest.contains(".WebRefreshJobService"));
        assertTrue(manifest.contains("android.permission.BIND_JOB_SERVICE"));
    }
}
