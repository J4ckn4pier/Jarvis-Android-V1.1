package com.jarvis.mobile.actions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

/** Typed SMS compose capability. Sending remains approval-gated by the shared tool policy. */
public final class AndroidMessagingActions {
    private final Context context;

    public AndroidMessagingActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String prepareMessage(String recipient, String message) {
        String target = clean(recipient);
        String body = clean(message);
        if (target.isBlank()) return "Recipient must be specified.";
        if (body.isBlank()) return "Message must be specified.";

        try {
            String number = looksLikeNumber(target) ? target : phoneFor(target);
            if (number == null || number.isBlank()) {
                return hasContactsPermission()
                        ? "I couldn’t find " + target + " in your contacts."
                        : "Enable Contacts permission so I can resolve that name.";
            }

            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("smsto:" + Uri.encode(number)));
            intent.putExtra("sms_body", body);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible messaging app is available.";
            }
            context.startActivity(intent);
            return "Message ready for " + target + ".";
        } catch (SecurityException denied) {
            return "Android blocked messaging because a required permission is off.";
        } catch (Exception failure) {
            String detail = failure.getMessage();
            return "Messaging failed: " + (detail == null ? failure.getClass().getSimpleName() : detail);
        }
    }

    private String phoneFor(String name) {
        if (!hasContactsPermission()) return null;
        Uri filter = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(name));
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        try (Cursor cursor = context.getContentResolver().query(filter, projection, null, null, null)) {
            if (cursor == null) return null;
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            String fallback = null;
            while (cursor.moveToNext()) {
                String number = numberIndex < 0 ? null : cursor.getString(numberIndex);
                if (number == null || number.isBlank()) continue;
                if (fallback == null) fallback = number;
                String displayName = nameIndex < 0 ? null : cursor.getString(nameIndex);
                if (displayName != null && displayName.equalsIgnoreCase(name)) return number;
            }
            return fallback;
        }
    }

    private boolean hasContactsPermission() {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean looksLikeNumber(String value) {
        if (value == null) return false;
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() >= 3 && value.matches("[+()0-9 .-]+");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
