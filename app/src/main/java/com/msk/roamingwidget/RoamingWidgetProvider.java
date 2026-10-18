package com.msk.roamingwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class RoamingWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.msk.roamingwidget.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetUpdater.updateOne(context, appWidgetManager, appWidgetId);
        }
        Context appContext = context.getApplicationContext();
        RefreshJobService.schedulePeriodic(appContext);
        RefreshJobService.scheduleImmediate(appContext);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId,
            Bundle newOptions
    ) {
        WidgetUpdater.updateOne(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onEnabled(Context context) {
        RefreshJobService.schedulePeriodic(context.getApplicationContext());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            RefreshJobService.scheduleImmediate(context.getApplicationContext());
        }
    }
}
