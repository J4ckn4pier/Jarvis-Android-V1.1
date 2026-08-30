package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import java.util.Locale;

/** Typed Android timer capability that preserves structured duration arguments. */
public final class AndroidTimerActions {
    private final Context context;

    public AndroidTimerActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String setTimer(String amount, String unit) {
        String amountText = amount == null ? "" : amount.trim();
        String unitText = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (amountText.isEmpty() || unitText.isEmpty()) {
            return "Tell me how long to set the timer for.";
        }

        final long quantity;
        try {
            quantity = Long.parseLong(amountText);
        } catch (NumberFormatException invalid) {
            return "I couldn't understand that timer duration.";
        }
        if (quantity <= 0) return "Timer duration must be greater than zero.";

        final long multiplier;
        switch (unitText) {
            case "second":
            case "seconds": multiplier = 1L; break;
            case "minute":
            case "minutes": multiplier = 60L; break;
            case "hour":
            case "hours": multiplier = 3600L; break;
            case "day":
            case "days": multiplier = 86400L; break;
            default: return "I couldn't understand that timer unit.";
        }

        final long seconds;
        try {
            seconds = Math.multiplyExact(quantity, multiplier);
        } catch (ArithmeticException overflow) {
            return "That timer duration is too large.";
        }
        if (seconds > Integer.MAX_VALUE) return "That timer duration is too large.";

        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, (int) seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible timer app is available.";
            }
            context.startActivity(intent);
            return "Timer set for " + quantity + " " + unitText + ".";
        } catch (SecurityException denied) {
            return "Android blocked that timer action.";
        } catch (Exception unavailable) {
            return "No compatible timer app is available.";
        }
    }
}
