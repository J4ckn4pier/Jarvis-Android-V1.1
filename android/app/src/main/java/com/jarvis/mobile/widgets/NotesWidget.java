package com.jarvis.mobile.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.jarvis.mobile.NotesActivity;
import com.jarvis.mobile.R;
import com.jarvis.mobile.memory.JarvisDatabase;

/** Working version of the donor Jarvis Notes widget. */
public class NotesWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        JarvisDatabase db = JarvisDatabase.get(context);
        String badge = String.valueOf(db.memoryCount() + db.openTaskCount());
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_notes);
            views.setTextViewText(R.id.widget_note_badge, badge);
            Intent open = new Intent(context, NotesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pending = PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_notes_root, pending);
            manager.updateAppWidget(id, views);
        }
    }
}
