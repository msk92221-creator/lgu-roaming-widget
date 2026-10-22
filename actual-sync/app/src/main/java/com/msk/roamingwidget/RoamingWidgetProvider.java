package com.msk.roamingwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class RoamingWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.msk.roamingwidget.action.WEB_REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            BackgroundRefresh.request(context);
            return;
        }
        super.onReceive(context, intent);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            WidgetUpdater.updateAll(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetUpdater.updateOne(context, manager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager manager,
            int appWidgetId,
            Bundle newOptions
    ) {
        WidgetUpdater.updateOne(context, manager, appWidgetId);
    }
}
