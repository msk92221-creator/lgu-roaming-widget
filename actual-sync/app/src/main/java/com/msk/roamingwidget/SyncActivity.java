package com.msk.roamingwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public final class SyncActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AccessibilityStatus.isEnabled(this)) {
            Toast.makeText(this, R.string.toast_accessibility_first, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            finish();
            return;
        }
        OfficialUsageStore.beginSync(this);
        WidgetUpdater.updateAll(this);
        UplusAccessibilityService.requestScan();
        Toast.makeText(this, R.string.toast_sync_started, Toast.LENGTH_LONG).show();
        UplusLauncher.launch(this);
        finish();
    }
}
