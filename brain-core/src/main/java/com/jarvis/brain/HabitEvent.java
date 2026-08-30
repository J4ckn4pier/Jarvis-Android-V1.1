package com.jarvis.brain;

import java.time.DayOfWeek;

public record HabitEvent(String action, DayOfWeek dayOfWeek, int hour, String context) {
    public HabitEvent {
        context = context == null ? "" : context;
    }
}
