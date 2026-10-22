package com.msk.roamingwidget;

import android.content.Context;

/** Only an onboarding flag: authentication itself stays in the app's WebView cookie jar. */
final class WebSessionState {
    private static final String FILE = "official_web_session";
    private WebSessionState() { }

    static boolean isConfigured(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean("configured", false);
    }

    static void markConfigured(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean("configured", true).apply();
    }
}
