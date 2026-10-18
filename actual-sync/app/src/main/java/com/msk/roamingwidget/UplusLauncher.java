package com.msk.roamingwidget;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

final class UplusLauncher {
    static final String UPLUS_PACKAGE = "com.lguplus.mobile.cs";
    private static final String ROAMING_USAGE_DEEP_LINK =
            "upapp://smsurl?url=%2Fplan%2Froaming%2Fmy-roaming";
    private static final String PLAY_URI = "market://details?id=" + UPLUS_PACKAGE;
    private static final String PLAY_WEB =
            "https://play.google.com/store/apps/details?id=" + UPLUS_PACKAGE;

    private UplusLauncher() {
    }

    static void launch(Activity activity) {
        Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse(ROAMING_USAGE_DEEP_LINK))
                .setPackage(UPLUS_PACKAGE)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            activity.startActivity(direct);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Fall through to the normal app launcher for older U+one versions.
        }

        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(UPLUS_PACKAGE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(launch);
            return;
        }

        Toast.makeText(activity, R.string.toast_uplus_missing, Toast.LENGTH_LONG).show();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URI)));
        } catch (ActivityNotFoundException exception) {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_WEB)));
        }
    }
}
