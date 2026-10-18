package com.msk.roamingwidget;

import android.content.Context;
import android.content.SharedPreferences;

final class RoamingPreferences {
    private static final String FILE_NAME = "roaming_widget";
    private static final String KEY_CONFIGURED = "configured";
    private static final String KEY_TOTAL_BYTES = "total_bytes";
    private static final String KEY_ANCHOR_REMAINING_BYTES = "anchor_remaining_bytes";
    private static final String KEY_ESTIMATED_REMAINING_BYTES = "estimated_remaining_bytes";
    private static final String KEY_WINDOW_START = "window_start";
    private static final String KEY_BASELINE_COUNTER = "baseline_counter";
    private static final String KEY_BASELINE_VALID = "baseline_valid";
    private static final String KEY_CALIBRATED_AT = "calibrated_at";
    private static final String KEY_REFRESHED_AT = "refreshed_at";
    private static final String KEY_LAST_ATTEMPT_AT = "last_attempt_at";
    private static final String KEY_STATUS = "status";

    static final String STATUS_READY = "ready";
    static final String STATUS_PERMISSION_REQUIRED = "permission_required";
    static final String STATUS_ERROR = "error";

    private RoamingPreferences() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    static Snapshot read(Context context) {
        SharedPreferences preferences = preferences(context);
        boolean configured = preferences.getBoolean(KEY_CONFIGURED, false);
        long totalBytes = preferences.getLong(KEY_TOTAL_BYTES, 0L);
        long anchorRemainingBytes = preferences.getLong(KEY_ANCHOR_REMAINING_BYTES, 0L);
        return new Snapshot(
                configured,
                totalBytes,
                anchorRemainingBytes,
                preferences.getLong(KEY_ESTIMATED_REMAINING_BYTES, anchorRemainingBytes),
                preferences.getLong(KEY_WINDOW_START, 0L),
                preferences.getLong(KEY_BASELINE_COUNTER, 0L),
                preferences.getBoolean(KEY_BASELINE_VALID, false),
                preferences.getLong(KEY_CALIBRATED_AT, 0L),
                preferences.getLong(KEY_REFRESHED_AT, 0L),
                preferences.getLong(KEY_LAST_ATTEMPT_AT, 0L),
                preferences.getString(KEY_STATUS, STATUS_PERMISSION_REQUIRED)
        );
    }

    static void saveCalibration(
            Context context,
            long totalBytes,
            long remainingBytes,
            long windowStart,
            String status,
            long timestamp
    ) {
        preferences(context).edit()
                .putBoolean(KEY_CONFIGURED, true)
                .putLong(KEY_TOTAL_BYTES, totalBytes)
                .putLong(KEY_ANCHOR_REMAINING_BYTES, remainingBytes)
                .putLong(KEY_ESTIMATED_REMAINING_BYTES, remainingBytes)
                .putLong(KEY_WINDOW_START, windowStart)
                .putLong(KEY_BASELINE_COUNTER, 0L)
                .putBoolean(KEY_BASELINE_VALID, true)
                .putLong(KEY_CALIBRATED_AT, timestamp)
                .putLong(KEY_REFRESHED_AT, 0L)
                .putLong(KEY_LAST_ATTEMPT_AT, timestamp)
                .putString(KEY_STATUS, status)
                .apply();
    }

    static void saveRefresh(
            Context context,
            long estimatedRemainingBytes,
            long latestCounter,
            long timestamp
    ) {
        preferences(context).edit()
                .putLong(KEY_ESTIMATED_REMAINING_BYTES, estimatedRemainingBytes)
                .putLong(KEY_BASELINE_COUNTER, latestCounter)
                .putBoolean(KEY_BASELINE_VALID, true)
                .putLong(KEY_REFRESHED_AT, timestamp)
                .putLong(KEY_LAST_ATTEMPT_AT, timestamp)
                .putString(KEY_STATUS, STATUS_READY)
                .apply();
    }

    static void saveStatus(Context context, String status, long attemptTimestamp) {
        preferences(context).edit()
                .putLong(KEY_LAST_ATTEMPT_AT, attemptTimestamp)
                .putString(KEY_STATUS, status)
                .apply();
    }

    static final class Snapshot {
        final boolean configured;
        final long totalBytes;
        final long anchorRemainingBytes;
        final long estimatedRemainingBytes;
        final long windowStart;
        final long baselineCounter;
        final boolean baselineValid;
        final long calibratedAt;
        final long refreshedAt;
        final long lastAttemptAt;
        final String status;

        Snapshot(
                boolean configured,
                long totalBytes,
                long anchorRemainingBytes,
                long estimatedRemainingBytes,
                long windowStart,
                long baselineCounter,
                boolean baselineValid,
                long calibratedAt,
                long refreshedAt,
                long lastAttemptAt,
                String status
        ) {
            this.configured = configured;
            this.totalBytes = totalBytes;
            this.anchorRemainingBytes = anchorRemainingBytes;
            this.estimatedRemainingBytes = estimatedRemainingBytes;
            this.windowStart = windowStart;
            this.baselineCounter = baselineCounter;
            this.baselineValid = baselineValid;
            this.calibratedAt = calibratedAt;
            this.refreshedAt = refreshedAt;
            this.lastAttemptAt = lastAttemptAt;
            this.status = status;
        }
    }
}
