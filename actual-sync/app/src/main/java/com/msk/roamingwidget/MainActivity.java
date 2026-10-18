package com.msk.roamingwidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView summaryValue;
    private TextView summaryMeta;
    private TextView permissionStatus;
    private TextView syncStatus;
    private TextView diagnosticText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        summaryValue = findViewById(R.id.summaryValue);
        summaryMeta = findViewById(R.id.summaryMeta);
        permissionStatus = findViewById(R.id.permissionStatus);
        syncStatus = findViewById(R.id.syncStatus);
        diagnosticText = findViewById(R.id.diagnosticText);

        applySystemBarInsets(findViewById(R.id.mainRoot));
        findViewById(R.id.permissionButton).setOnClickListener(view -> openAccessibilitySettings());
        findViewById(R.id.appInfoButton).setOnClickListener(view -> openAppInfo());
        findViewById(R.id.openUplusButton).setOnClickListener(view -> startSync());
        findViewById(R.id.copyDiagnosticButton).setOnClickListener(view -> copyDiagnostic());
        findViewById(R.id.addWidgetButton).setOnClickListener(view -> requestWidgetPin());
        updateScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreen();
    }

    private void updateScreen() {
        OfficialUsageStore.Snapshot snapshot = OfficialUsageStore.read(this);
        boolean accessibilityEnabled = AccessibilityStatus.isEnabled(this);

        if (snapshot.hasValue) {
            summaryValue.setText(snapshot.primaryText);
            int meta = UplusScreenParser.Kind.DISPLAYED_USED_OF_TOTAL.name().equals(snapshot.kind)
                    ? R.string.displayed_usage_meta
                    : R.string.captured_meta;
            summaryMeta.setText(getString(meta, TimeFormat.relative(this, snapshot.capturedAt)));
            if (!snapshot.usedText.isEmpty()) {
                summaryMeta.append("\n사용 " + snapshot.usedText + " · 전체 " + snapshot.totalText
                        + "\n사용량 = U+one 전체량 − 남은량");
            }
        } else {
            summaryValue.setText(R.string.not_captured);
            summaryMeta.setText(R.string.not_captured_meta);
        }

        permissionStatus.setText(accessibilityEnabled
                ? R.string.permission_allowed
                : R.string.permission_required);
        permissionStatus.setTextColor(getColor(accessibilityEnabled ? R.color.success : R.color.error));

        if (OfficialUsageStore.STATUS_READY.equals(snapshot.status)) {
            syncStatus.setText(R.string.sync_success);
            syncStatus.setTextColor(getColor(R.color.success));
        } else if (OfficialUsageStore.STATUS_WAITING.equals(snapshot.status)) {
            syncStatus.setText(R.string.sync_waiting);
            syncStatus.setTextColor(getColor(R.color.warning));
        } else if (OfficialUsageStore.STATUS_SCREEN_NO_VALUE.equals(snapshot.status)) {
            syncStatus.setText(R.string.screen_found_no_value);
            syncStatus.setTextColor(getColor(R.color.error));
        } else if (OfficialUsageStore.STATUS_AMBIGUOUS.equals(snapshot.status)) {
            syncStatus.setText(R.string.ambiguous_value);
            syncStatus.setTextColor(getColor(R.color.error));
        } else if (OfficialUsageStore.STATUS_LOGIN_REQUIRED.equals(snapshot.status)) {
            syncStatus.setText(R.string.login_required);
            syncStatus.setTextColor(getColor(R.color.warning));
        } else {
            syncStatus.setText(R.string.sync_not_running);
            syncStatus.setTextColor(getColor(R.color.ink));
        }

        diagnosticText.setText(snapshot.diagnostic.isEmpty()
                ? getString(R.string.diagnostic_empty)
                : snapshot.diagnostic);
    }

    private void startSync() {
        if (!AccessibilityStatus.isEnabled(this)) {
            Toast.makeText(this, R.string.toast_accessibility_first, Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }
        OfficialUsageStore.beginSync(this);
        updateScreen();
        Toast.makeText(this, R.string.toast_sync_started, Toast.LENGTH_LONG).show();
        UplusLauncher.launch(this);
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            openAppInfo();
        }
    }

    private void openAppInfo() {
        startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        ));
    }

    private void copyDiagnostic() {
        OfficialUsageStore.Snapshot snapshot = OfficialUsageStore.read(this);
        String diagnostic = snapshot.diagnostic.isEmpty()
                ? getString(R.string.diagnostic_empty)
                : snapshot.diagnostic;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("U+one 로밍 진단", diagnostic));
            Toast.makeText(this, R.string.toast_diagnostic_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestWidgetPin() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, RoamingWidgetProvider.class);
        if (!manager.isRequestPinAppWidgetSupported()) {
            Toast.makeText(this, R.string.toast_add_widget_manually, Toast.LENGTH_LONG).show();
            return;
        }
        Intent callbackIntent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent callback = PendingIntent.getActivity(
                this,
                40_001,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (!manager.requestPinAppWidget(provider, null, callback)) {
            Toast.makeText(this, R.string.toast_add_widget_manually, Toast.LENGTH_LONG).show();
        }
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );
            }
            return insets;
        });
        root.requestApplyInsets();
    }
}
