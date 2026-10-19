package com.msk.roamingwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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
        WidgetBalanceDisplay display = WidgetBalanceDisplay.from(snapshot.hasValue,
                snapshot.remainingText, snapshot.usedText, snapshot.totalText);
        Bitmap bar = WidgetBarRenderer.render(display.remainingFraction);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
            layouts.put(new SizeF(180f, 72f), createViews(context, appWidgetId, LAYOUT_COMPACT, snapshot, display, bar));
            layouts.put(new SizeF(250f, 84f), createViews(context, appWidgetId, LAYOUT_STANDARD, snapshot, display, bar));
            layouts.put(new SizeF(320f, 84f), createViews(context, appWidgetId, LAYOUT_WIDE, snapshot, display, bar));
            manager.updateAppWidget(appWidgetId, new RemoteViews(layouts));
            return;
        }

        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 84);
        int layout = minWidth < 250 || minHeight < 84
                ? LAYOUT_COMPACT : minWidth >= 320 ? LAYOUT_WIDE : LAYOUT_STANDARD;
        manager.updateAppWidget(appWidgetId, createViews(context, appWidgetId, layout, snapshot, display, bar));
    }

    private static RemoteViews createViews(
            Context context,
            int appWidgetId,
            int layout,
            OfficialUsageStore.Snapshot snapshot,
            WidgetBalanceDisplay display,
            Bitmap bar
    ) {
        int layoutId = layout == LAYOUT_COMPACT
                ? R.layout.widget_roaming_compact
                : layout == LAYOUT_WIDE ? R.layout.widget_roaming_wide : R.layout.widget_roaming;
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        PendingIntent sync = syncIntent(context, appWidgetId);
        views.setOnClickPendingIntent(R.id.widgetRoot, sync);
        views.setOnClickPendingIntent(R.id.widgetRefresh, sync);

        String remaining = displayValue(context, snapshot.hasValue ? snapshot.remainingText : "");
        String used = displayValue(context, snapshot.hasValue ? snapshot.usedText : "");
        String total = displayValue(context, snapshot.hasValue ? snapshot.totalText : "");
        String captured = snapshot.hasValue
                ? TimeFormat.relative(context, snapshot.capturedAt)
                : context.getString(R.string.balance_not_synced);
        views.setImageViewBitmap(R.id.widgetBar, bar);
        views.setTextViewText(R.id.widgetValue, display.remainingLabel);
        views.setTextColor(R.id.widgetValue, display.remainingFraction >= 0.55
                ? Color.WHITE : Color.rgb(87, 93, 102));
        views.setTextViewText(R.id.widgetUsed, display.usedLabel);
        views.setTextViewText(R.id.widgetDetails, context.getString(R.string.reference_usage_caption));
        views.setTextViewText(R.id.widgetUpdated, snapshot.hasValue
                ? captured
                : context.getString(R.string.balance_tap_to_sync));
        views.setContentDescription(R.id.widgetRoot, context.getString(
                R.string.balance_content_description, used, remaining, total, captured));
        return views;
    }

    private static String displayValue(Context context, String value) {
        return value == null || value.trim().isEmpty()
                ? context.getString(R.string.balance_missing) : value;
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
