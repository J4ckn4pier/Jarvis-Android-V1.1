package com.jarvis.mobile.actions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import com.jarvis.brain.UniqueNamedTargetResolver;

import java.util.ArrayList;
import java.util.List;

/** Typed Android dialer/call capability independent of launcher labels or OEM phone-app names. */
public final class AndroidDialerActions {
    private final Context context;

    public AndroidDialerActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible dialer app is available.";
            }
            context.startActivity(intent);
            return "Dialer opened.";
        } catch (SecurityException denied) {
            return "Android blocked that action because its permission is off.";
        } catch (Exception unavailable) {
            return "No compatible dialer app is available.";
        }
    }

    /** Places the call only after the shared consequential-action approval gate has been consumed. */
    public String call(String recipient) {
        String target = clean(recipient);
        if (target.isBlank()) return "Recipient must be specified.";
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            return "Enable Phone permission so I can place that call.";
        }
        try {
            String number = looksLikeNumber(target) ? target : phoneFor(target);
            if (number == null || number.isBlank()) {
                return hasPermission(Manifest.permission.READ_CONTACTS)
                        ? "I couldn’t uniquely resolve a phone number for " + target + " in your contacts."
                        : "Enable Contacts permission so I can resolve that name.";
            }
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible calling app is available.";
            }
            context.startActivity(intent);
            return "Calling " + target + ".";
        } catch (SecurityException denied) {
            return "Android blocked calling because a required permission is off.";
        } catch (Exception unavailable) {
            return "Calling failed because no compatible phone service is available.";
        }
    }

    private String phoneFor(String name) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null;
        Uri filter = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(name));
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        List<UniqueNamedTargetResolver.Candidate> candidates = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(filter, projection, null, null, null)) {
            if (cursor == null) return null;
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                String number = numberIndex < 0 ? null : cursor.getString(numberIndex);
                String displayName = nameIndex < 0 ? null : cursor.getString(nameIndex);
                candidates.add(new UniqueNamedTargetResolver.Candidate(displayName, number));
            }
            return UniqueNamedTargetResolver.resolve(name, candidates).orElse(null);
        }
    }

    private boolean hasPermission(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
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
