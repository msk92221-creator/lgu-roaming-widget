package com.msk.roamingwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.LinkedHashMap;
import java.util.Map;

final class WidgetUpdater {
    private static final int LAYOUT_COMPACT = 0;
    private static final int LAYOUT_STANDARD = 1;
    private static final int LAYOUT_WIDE = 2;

    private WidgetUpdater() {
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, RoamingWidgetProvider.class);
        for (int appWidgetId : manager.getAppWidgetIds(provider)) {
            updateOne(context, manager, appWidgetId);
        }
    }

    static void updateOne(Context context, AppWidgetManager manager, int appWidgetId) {
        OfficialUsageStore.Snapshot snapshot = OfficialUsageStore.read(context);
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
            OfficialUsageStore.Snapshot snapshot
    ) {
        boolean compact = layout == LAYOUT_COMPACT;
        int layoutId = compact
                ? R.layout.widget_roaming_compact
                : layout == LAYOUT_WIDE ? R.layout.widget_roaming_wide : R.layout.widget_roaming;
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        PendingIntent sync = syncIntent(context, appWidgetId);
        views.setOnClickPendingIntent(R.id.widgetRoot, sync);
        if (!compact) {
            views.setOnClickPendingIntent(R.id.widgetRefresh, sync);
        }

        views.setTextViewText(
                R.id.widgetTitle,
                context.getString(compact ? R.string.widget_title_compact : R.string.widget_title)
        );
        if (snapshot.hasValue) {
            views.setTextViewText(R.id.widgetValue, snapshot.primaryText);
            views.setTextViewText(R.id.widgetDetails, snapshot.detailsText);
            if (!compact) {
                views.setTextViewText(
                        R.id.widgetUpdated,
                        context.getString(
                                R.string.widget_source,
                                TimeFormat.relative(context, snapshot.capturedAt)
                        )
                );
            }
        } else {
            views.setTextViewText(R.id.widgetValue, context.getString(R.string.widget_no_value));
            views.setTextViewText(R.id.widgetDetails, context.getString(R.string.widget_tap_to_sync));
            if (!compact) {
                views.setTextViewText(R.id.widgetUpdated, context.getString(R.string.widget_source_never));
            }
        }
        views.setContentDescription(
                R.id.widgetRoot,
                context.getString(R.string.widget_content_description)
        );
        return views;
    }

    private static PendingIntent syncIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, SyncActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                20_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
