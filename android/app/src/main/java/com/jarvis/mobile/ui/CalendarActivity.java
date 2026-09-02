package com.jarvis.mobile.ui;

import com.jarvis.brain.UiSection;

/** "Calendar" screen: customizable event list, per the canonical prototype's Calendar screen. */
public final class CalendarActivity extends UiListScreenActivity {
    @Override protected String screenTitle() { return "CALENDAR"; }
    @Override protected UiSection section() { return UiSection.CALENDAR; }
    @Override protected String itemLabel() { return "event"; }
    @Override protected String detailsHint() { return "When / where"; }
}
