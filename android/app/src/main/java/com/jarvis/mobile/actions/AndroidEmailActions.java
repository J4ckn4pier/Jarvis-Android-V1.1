package com.jarvis.mobile.actions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

/** Typed email compose capability. Opens a user-visible draft/review surface and never sends silently. */
public final class AndroidEmailActions {
    private final Context context;

    public AndroidEmailActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String prepareEmail(String recipient, String subject, String body) {
        String target = clean(recipient);
        String title = clean(subject);
        String message = clean(body);
        if (target.isBlank()) return "Recipient must be specified.";
        if (title.isBlank()) return "Subject must be specified.";
        if (message.isBlank()) return "Email body must be specified.";

        try {
            String address = looksLikeEmail(target) ? target : emailFor(target);
            if (address == null || address.isBlank()) {
                return hasContactsPermission()
                        ? "I couldn’t find an email address for " + target + " in your contacts."
                        : "Enable Contacts permission so I can resolve that name to an email address.";
            }

            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + Uri.encode(address)));
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
            intent.putExtra(Intent.EXTRA_TEXT, message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible email app is available.";
            }
            context.startActivity(intent);
            return "Email draft ready for " + target + ".";
        } catch (SecurityException denied) {
            return "Android blocked email composition because a required permission is off.";
        } catch (Exception failure) {
            String detail = failure.getMessage();
            return "Email composition failed: " + (detail == null ? failure.getClass().getSimpleName() : detail);
        }
    }

    private String emailFor(String name) {
        if (!hasContactsPermission()) return null;
        Uri filter = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(name));
        String[] projection = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
        };
        try (Cursor cursor = context.getContentResolver().query(filter, projection, null, null, null)) {
            if (cursor == null) return null;
            int addressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME);
            String fallback = null;
            while (cursor.moveToNext()) {
                String address = addressIndex < 0 ? null : cursor.getString(addressIndex);
                if (address == null || address.isBlank()) continue;
                if (fallback == null) fallback = address;
                String displayName = nameIndex < 0 ? null : cursor.getString(nameIndex);
                if (displayName != null && displayName.equalsIgnoreCase(name)) return address;
            }
            return fallback;
        }
    }

    private boolean hasContactsPermission() {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean looksLikeEmail(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        return at > 0 && at < trimmed.length() - 1 && trimmed.indexOf('.', at) > at + 1;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
