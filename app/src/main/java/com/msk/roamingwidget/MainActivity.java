package com.msk.roamingwidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String UPLUS_USAGE_URL = "https://www.lguplus.com/plan/roaming/my-roaming";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText totalInput;
    private EditText remainingInput;
    private TextView summaryRemaining;
    private TextView summaryMeta;
    private TextView permissionStatus;
    private Button saveButton;
    private Button refreshButton;
    private boolean busy;
    private boolean inputsInitialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        totalInput = findViewById(R.id.totalInput);
        remainingInput = findViewById(R.id.remainingInput);
        summaryRemaining = findViewById(R.id.summaryRemaining);
        summaryMeta = findViewById(R.id.summaryMeta);
        permissionStatus = findViewById(R.id.permissionStatus);
        saveButton = findViewById(R.id.saveButton);
        refreshButton = findViewById(R.id.refreshButton);

        applySystemBarInsets(findViewById(R.id.mainRoot));

        findViewById(R.id.openUplusButton).setOnClickListener(view -> openUplus());
        findViewById(R.id.permissionButton).setOnClickListener(view -> openUsageAccessSettings());
        findViewById(R.id.appInfoButton).setOnClickListener(view -> openAppInfo());
        findViewById(R.id.addWidgetButton).setOnClickListener(view -> requestWidgetPin());
        saveButton.setOnClickListener(view -> saveCalibration());
        refreshButton.setOnClickListener(view -> refreshNow(true));

        updateScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreen();
        RoamingPreferences.Snapshot snapshot = RoamingPreferences.read(this);
        if (!busy
                && snapshot.configured
                && UsageRepository.hasUsageAccess(this)
                && RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(snapshot.status)) {
            refreshNow(false);
        }
    }

    private void updateScreen() {
        RoamingPreferences.Snapshot snapshot = RoamingPreferences.read(this);
        boolean hasAccess = UsageRepository.hasUsageAccess(this);

        if (snapshot.configured) {
            summaryRemaining.setText(getString(
                    R.string.remaining_format,
                    FormatUtils.formatBytes(snapshot.estimatedRemainingBytes)
            ));
            if (!inputsInitialized) {
                totalInput.setText(FormatUtils.formatInputGb(snapshot.totalBytes));
                remainingInput.setText(FormatUtils.formatInputGb(snapshot.estimatedRemainingBytes));
                inputsInitialized = true;
            }
        } else {
            summaryRemaining.setText(R.string.screen_unconfigured);
        }

        if (!snapshot.configured) {
            summaryMeta.setText(R.string.summary_unconfigured_meta);
        } else if (RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(snapshot.status)) {
            summaryMeta.setText(getString(
                    R.string.summary_permission,
                    FormatUtils.absoluteTime(snapshot.calibratedAt)
            ));
        } else if (RoamingPreferences.STATUS_ERROR.equals(snapshot.status)) {
            String lastSuccess = snapshot.refreshedAt > 0L
                    ? FormatUtils.relativeTime(this, snapshot.refreshedAt)
                    : getString(R.string.no_success_yet);
            summaryMeta.setText(getString(
                    R.string.summary_error,
                    lastSuccess,
                    FormatUtils.absoluteTime(snapshot.calibratedAt)
            ));
        } else {
            summaryMeta.setText(getString(
                    R.string.summary_ready,
                    FormatUtils.relativeTime(this, snapshot.refreshedAt),
                    FormatUtils.absoluteTime(snapshot.calibratedAt)
            ));
        }

        permissionStatus.setText(hasAccess
                ? R.string.permission_allowed
                : R.string.permission_required);
        permissionStatus.setTextColor(getColor(hasAccess ? R.color.success : R.color.error));
        refreshButton.setEnabled(!busy && snapshot.configured);
        saveButton.setEnabled(!busy);
    }

    private void saveCalibration() {
        totalInput.setError(null);
        remainingInput.setError(null);
        Double totalGb = parsePositiveNumber(totalInput);
        if (totalGb == null) {
            totalInput.setError(getString(R.string.error_positive_number));
            totalInput.requestFocus();
            return;
        }
        Double remainingGb = parseNonNegativeNumber(remainingInput);
        if (remainingGb == null) {
            remainingInput.setError(getString(R.string.error_non_negative_number));
            remainingInput.requestFocus();
            return;
        }
        if (remainingGb > totalGb) {
            remainingInput.setError(getString(R.string.error_remaining_over_total));
            remainingInput.requestFocus();
            return;
        }

        setBusy(true, true);
        long totalBytes = FormatUtils.gbToBytes(totalGb);
        long remainingBytes = FormatUtils.gbToBytes(remainingGb);
        AppExecutors.IO.execute(() -> {
            RoamingTracker.calibrate(getApplicationContext(), totalBytes, remainingBytes);
            RoamingPreferences.Snapshot result = RoamingPreferences.read(getApplicationContext());
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setBusy(false, true);
                inputsInitialized = false;
                updateScreen();
                int message = RoamingPreferences.STATUS_READY.equals(result.status)
                        ? R.string.toast_saved_tracking
                        : RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(result.status)
                        ? R.string.toast_saved_permission
                        : R.string.toast_saved_error;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void refreshNow(boolean showToast) {
        if (busy) {
            return;
        }
        setBusy(true, false);
        AppExecutors.IO.execute(() -> {
            RoamingTracker.refresh(getApplicationContext());
            RoamingPreferences.Snapshot result = RoamingPreferences.read(getApplicationContext());
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setBusy(false, false);
                updateScreen();
                if (showToast) {
                    int message = RoamingPreferences.STATUS_READY.equals(result.status)
                            ? R.string.toast_refreshed
                            : RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(result.status)
                            ? R.string.toast_refresh_permission
                            : R.string.toast_refresh_error;
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setBusy(boolean value, boolean saving) {
        busy = value;
        saveButton.setText(value && saving ? R.string.saving_button : R.string.save_button);
        refreshButton.setText(value && !saving ? R.string.refreshing : R.string.refresh_now);
        updateScreen();
    }

    private static Double parsePositiveNumber(EditText input) {
        Double value = parseNumber(input);
        return value != null && value > 0d ? value : null;
    }

    private static Double parseNonNegativeNumber(EditText input) {
        Double value = parseNumber(input);
        return value != null && value >= 0d ? value : null;
    }

    private static Double parseNumber(EditText input) {
        try {
            double value = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
            if (!Double.isFinite(value) || value > 1_000_000d) {
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void openUplus() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UPLUS_USAGE_URL)));
    }

    private void openUsageAccessSettings() {
        Intent direct = new Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        );
        try {
            startActivity(direct);
        } catch (ActivityNotFoundException exception) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private void openAppInfo() {
        startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        ));
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
