package com.msk.roamingwidget;

import android.content.Context;
import android.content.SharedPreferences;

final class OfficialUsageStore {
    static final String STATUS_NOT_STARTED = "not_started";
    static final String STATUS_WAITING = "waiting";
    static final String STATUS_READY = "ready";
    static final String STATUS_SCREEN_NO_VALUE = "screen_no_value";
    static final String STATUS_AMBIGUOUS = "ambiguous";
    static final String STATUS_LOGIN_REQUIRED = "login_required";

    private static final String FILE_NAME = "uplus_actual_usage_v2";
    private static final String LEGACY_FILE_NAME = "roaming_widget";
    private static final String KEY_MIGRATED = "legacy_estimate_removed";
    private static final String KEY_HAS_VALUE = "has_official_value";
    private static final String KEY_PRIMARY = "official_primary_text";
    private static final String KEY_DETAILS = "official_details_text";
    private static final String KEY_KIND = "official_kind";
    private static final String KEY_CAPTURED_AT = "captured_at";
    private static final String KEY_STATUS = "scan_status";
    private static final String KEY_DIAGNOSTIC = "diagnostic";
    private static final String KEY_LAST_SCAN_AT = "last_scan_at";
    private static final String KEY_SYNC_REQUESTED_AT = "sync_requested_at";
    private static final long SESSION_LENGTH_MS = 10L * 60L * 1000L;

    private OfficialUsageStore() {
    }

    static Snapshot read(Context context) {
        SharedPreferences preferences = preferences(context);
        migrateLegacyEstimate(context, preferences);
        long syncRequestedAt = preferences.getLong(KEY_SYNC_REQUESTED_AT, 0L);
        String status = preferences.getString(KEY_STATUS, STATUS_NOT_STARTED);
        if (STATUS_WAITING.equals(status) && !isTimestampActive(syncRequestedAt)) {
            status = STATUS_NOT_STARTED;
            preferences.edit()
                    .putString(KEY_STATUS, STATUS_NOT_STARTED)
                    .putLong(KEY_SYNC_REQUESTED_AT, 0L)
                    .apply();
            syncRequestedAt = 0L;
        }
        return new Snapshot(
                preferences.getBoolean(KEY_HAS_VALUE, false),
                preferences.getString(KEY_PRIMARY, ""),
                preferences.getString(KEY_DETAILS, ""),
                preferences.getString(KEY_KIND, ""),
                preferences.getLong(KEY_CAPTURED_AT, 0L),
                status,
                preferences.getString(KEY_DIAGNOSTIC, ""),
                preferences.getLong(KEY_LAST_SCAN_AT, 0L),
                syncRequestedAt
        );
    }

    static void beginSync(Context context) {
        long now = System.currentTimeMillis();
        preferences(context).edit()
                .putString(KEY_STATUS, STATUS_WAITING)
                .putLong(KEY_SYNC_REQUESTED_AT, now)
                .putLong(KEY_LAST_SCAN_AT, 0L)
                .apply();
    }

    static boolean isSyncSessionActive(Context context) {
        return isTimestampActive(preferences(context).getLong(KEY_SYNC_REQUESTED_AT, 0L));
    }

    static void saveSuccess(Context context, UplusScreenParser.Result result) {
        long now = System.currentTimeMillis();
        preferences(context).edit()
                .putBoolean(KEY_HAS_VALUE, true)
                .putString(KEY_PRIMARY, result.primaryText)
                .putString(KEY_DETAILS, result.detailsText)
                .putString(KEY_KIND, result.kind.name())
                .putLong(KEY_CAPTURED_AT, now)
                .putString(KEY_STATUS, STATUS_READY)
                .putString(KEY_DIAGNOSTIC, result.diagnostic)
                .putLong(KEY_LAST_SCAN_AT, now)
                .putLong(KEY_SYNC_REQUESTED_AT, 0L)
                .apply();
    }

    static void saveScanResult(Context context, UplusScreenParser.Result result) {
        long now = System.currentTimeMillis();
        String status;
        switch (result.status) {
            case AMBIGUOUS:
                status = STATUS_AMBIGUOUS;
                break;
            case LOGIN_REQUIRED:
                status = STATUS_LOGIN_REQUIRED;
                break;
            case NO_VALUE:
                status = STATUS_SCREEN_NO_VALUE;
                break;
            default:
                status = STATUS_WAITING;
                break;
        }
        preferences(context).edit()
                .putString(KEY_STATUS, status)
                .putString(KEY_DIAGNOSTIC, result.diagnostic)
                .putLong(KEY_LAST_SCAN_AT, now)
                .apply();
    }

    private static boolean isTimestampActive(long timestamp) {
        long age = System.currentTimeMillis() - timestamp;
        return timestamp > 0L && age >= 0L && age <= SESSION_LENGTH_MS;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    private static void migrateLegacyEstimate(Context context, SharedPreferences current) {
        if (current.getBoolean(KEY_MIGRATED, false)) {
            return;
        }
        context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        current.edit().putBoolean(KEY_MIGRATED, true).apply();
    }

    static final class Snapshot {
        final boolean hasValue;
        final String primaryText;
        final String detailsText;
        final String kind;
        final long capturedAt;
        final String status;
        final String diagnostic;
        final long lastScanAt;
        final long syncRequestedAt;

        Snapshot(
                boolean hasValue,
                String primaryText,
                String detailsText,
                String kind,
                long capturedAt,
                String status,
                String diagnostic,
                long lastScanAt,
                long syncRequestedAt
        ) {
            this.hasValue = hasValue;
            this.primaryText = primaryText;
            this.detailsText = detailsText;
            this.kind = kind;
            this.capturedAt = capturedAt;
            this.status = status;
            this.diagnostic = diagnostic;
            this.lastScanAt = lastScanAt;
            this.syncRequestedAt = syncRequestedAt;
        }
    }
}
