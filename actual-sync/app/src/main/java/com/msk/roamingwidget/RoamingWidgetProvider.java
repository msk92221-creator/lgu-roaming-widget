package com.msk.roamingwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public final class RoamingWidgetProvider extends AppWidgetProvider {
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
