package com.msk.roamingwidget;

import android.content.Context;

final class RoamingTracker {
    private RoamingTracker() {
    }

    static void calibrate(Context context, long totalBytes, long remainingBytes) {
        Context appContext = context.getApplicationContext();
        long now = System.currentTimeMillis();
        boolean hasAccess = UsageRepository.hasUsageAccess(appContext);
        RoamingPreferences.saveCalibration(
                appContext,
                totalBytes,
                remainingBytes,
                now,
                hasAccess ? RoamingPreferences.STATUS_READY : RoamingPreferences.STATUS_PERMISSION_REQUIRED,
                now
        );

        if (hasAccess) {
            refresh(appContext);
        } else {
            WidgetUpdater.updateAll(appContext);
        }
        RefreshJobService.schedulePeriodic(appContext);
    }

    static void refresh(Context context) {
        Context appContext = context.getApplicationContext();
        RoamingPreferences.Snapshot snapshot = RoamingPreferences.read(appContext);
        if (!snapshot.configured) {
            WidgetUpdater.updateAll(appContext);
            return;
        }

        long now = System.currentTimeMillis();
        if (!UsageRepository.hasUsageAccess(appContext)) {
            RoamingPreferences.saveStatus(appContext, RoamingPreferences.STATUS_PERMISSION_REQUIRED, now);
            WidgetUpdater.updateAll(appContext);
            return;
        }

        try {
            long currentCounter = UsageRepository.queryRoamingMobileBytes(
                    appContext,
                    snapshot.windowStart,
                    now
            );
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            long previousCounter = snapshot.baselineValid ? snapshot.baselineCounter : 0L;
            long previousRemaining = snapshot.baselineValid
                    ? snapshot.estimatedRemainingBytes
                    : snapshot.anchorRemainingBytes;
            long remaining = RoamingMath.calculateRemaining(
                    previousRemaining,
                    previousCounter,
                    currentCounter
            );
            RoamingPreferences.saveRefresh(appContext, remaining, currentCounter, now);
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            RoamingPreferences.saveStatus(appContext, RoamingPreferences.STATUS_ERROR, now);
        }
        WidgetUpdater.updateAll(appContext);
    }
}
