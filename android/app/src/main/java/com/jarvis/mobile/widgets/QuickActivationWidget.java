package com.jarvis.mobile.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.jarvis.mobile.MainActivity;
import com.jarvis.mobile.R;

/** Working version of the donor Jarvis Quick Access home-screen widget. */
public class QuickActivationWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_activation);
            Intent launch = new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_VOICE_COMMAND)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, id, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_quick_root, pending);
            manager.updateAppWidget(id, views);
        }
    }
}
