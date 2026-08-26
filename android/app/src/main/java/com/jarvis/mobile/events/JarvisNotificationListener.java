package com.jarvis.mobile.events;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.jarvis.mobile.memory.JarvisDatabase;

public class JarvisNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (notification == null || notification.getPackageName().equals(getPackageName())) return;
        Notification value = notification.getNotification();
        CharSequence title = value.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence bigText = value.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence text = bigText != null ? bigText : value.extras.getCharSequence(Notification.EXTRA_TEXT);
        JarvisDatabase.get(this).logEvent(
                "notification",
                applicationLabel(notification.getPackageName()),
                title == null ? "" : title.toString(),
                text == null ? "" : text.toString());
    }

    private String applicationLabel(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }
}
