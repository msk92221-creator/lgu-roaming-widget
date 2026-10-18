package com.msk.roamingwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.util.LinkedHashMap;
import java.util.Map;

final class WidgetUpdater {
    private static final String UPLUS_USAGE_URL = "https://www.lguplus.com/plan/roaming/my-roaming";
    private static final int LAYOUT_COMPACT = 0;
    private static final int LAYOUT_STANDARD = 1;
    private static final int LAYOUT_WIDE = 2;

    private WidgetUpdater() {
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, RoamingWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(provider)) {
            updateOne(context, manager, id);
        }
    }

    static void updateOne(Context context, AppWidgetManager manager, int appWidgetId) {
        RoamingPreferences.Snapshot snapshot = RoamingPreferences.read(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
            layouts.put(new SizeF(160f, 90f), createViews(context, appWidgetId, LAYOUT_COMPACT, snapshot));
            layouts.put(new SizeF(180f, 160f), createViews(context, appWidgetId, LAYOUT_STANDARD, snapshot));
            layouts.put(new SizeF(320f, 100f), createViews(context, appWidgetId, LAYOUT_WIDE, snapshot));
            manager.updateAppWidget(appWidgetId, new RemoteViews(layouts));
            return;
        }

        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160);
        int layout = minHeight < 150
                ? (minWidth >= 320 ? LAYOUT_WIDE : LAYOUT_COMPACT)
                : LAYOUT_STANDARD;
        manager.updateAppWidget(appWidgetId, createViews(context, appWidgetId, layout, snapshot));
    }

    private static RemoteViews createViews(
            Context context,
            int appWidgetId,
            int layout,
            RoamingPreferences.Snapshot snapshot
    ) {
        boolean compact = layout == LAYOUT_COMPACT;
        int layoutId = compact
                ? R.layout.widget_roaming_compact
                : layout == LAYOUT_WIDE ? R.layout.widget_roaming_wide : R.layout.widget_roaming;
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        PendingIntent openApp = openAppIntent(context, appWidgetId);
        PendingIntent refresh = refreshIntent(context, appWidgetId);
        boolean tapRefreshes = snapshot.configured
                && !RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(snapshot.status);
        views.setOnClickPendingIntent(R.id.widgetRoot, tapRefreshes ? refresh : openApp);
        views.setContentDescription(
                R.id.widgetRoot,
                snapshot.configured
                        ? context.getString(R.string.widget_content_description)
                        : context.getString(R.string.tap_to_configure)
        );

        if (!compact) {
            views.setOnClickPendingIntent(R.id.widgetRefresh, refresh);
        }

        if (!snapshot.configured) {
            views.setTextViewText(R.id.widgetRemaining, context.getString(R.string.not_configured));
            views.setTextViewText(R.id.widgetDetails, context.getString(R.string.tap_to_configure));
            if (!compact) {
                views.setProgressBar(R.id.widgetProgress, 1000, 0, false);
                views.setTextViewText(R.id.widgetUpdated, context.getString(R.string.open_uplus));
                views.setOnClickPendingIntent(R.id.widgetUpdated, openUplusIntent(context, appWidgetId));
            }
            return views;
        }

        long remaining = snapshot.estimatedRemainingBytes;
        long used = RoamingMath.usedBytes(snapshot.totalBytes, remaining);
        views.setTextViewText(
                R.id.widgetRemaining,
                context.getString(R.string.remaining_format, FormatUtils.formatBytes(remaining))
        );

        String details;
        if (RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(snapshot.status)) {
            details = context.getString(R.string.status_permission_widget);
        } else if (RoamingPreferences.STATUS_ERROR.equals(snapshot.status)) {
            details = context.getString(R.string.status_error_widget);
            views.setOnClickPendingIntent(R.id.widgetDetails, refresh);
        } else {
            details = context.getString(
                    R.string.usage_format,
                    FormatUtils.formatBytes(used),
                    FormatUtils.formatBytes(snapshot.totalBytes)
            );
        }
        views.setTextViewText(R.id.widgetDetails, details);

        if (compact) {
            views.setTextViewText(
                    R.id.widgetTitle,
                    RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(snapshot.status)
                            ? context.getString(R.string.widget_title)
                            : context.getString(R.string.widget_title_tap_refresh)
            );
            return views;
        }

        views.setProgressBar(
                R.id.widgetProgress,
                1000,
                RoamingMath.remainingPermille(snapshot.totalBytes, remaining),
                false
        );
        views.setTextViewText(R.id.widgetUpdated, freshnessText(context, snapshot));
        views.setOnClickPendingIntent(R.id.widgetUpdated, openUplusIntent(context, appWidgetId));
        return views;
    }

    private static String freshnessText(Context context, RoamingPreferences.Snapshot snapshot) {
        if (RoamingPreferences.STATUS_PERMISSION_REQUIRED.equals(snapshot.status)) {
            return context.getString(
                    R.string.freshness_uplus,
                    FormatUtils.absoluteTime(snapshot.calibratedAt)
            );
        }
        if (RoamingPreferences.STATUS_ERROR.equals(snapshot.status)) {
            return snapshot.refreshedAt > 0L
                    ? context.getString(
                            R.string.freshness_last_success,
                            FormatUtils.relativeTime(context, snapshot.refreshedAt)
                    )
                    : context.getString(R.string.freshness_no_success);
        }
        return context.getString(
                R.string.freshness_device,
                FormatUtils.relativeTime(context, snapshot.refreshedAt)
        );
    }

    private static PendingIntent openAppIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                10_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent refreshIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, RoamingWidgetProvider.class)
                .setAction(RoamingWidgetProvider.ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getBroadcast(
                context,
                20_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent openUplusIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(UPLUS_USAGE_URL));
        return PendingIntent.getActivity(
                context,
                30_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
