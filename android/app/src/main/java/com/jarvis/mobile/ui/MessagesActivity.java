package com.jarvis.mobile.ui;

import com.jarvis.brain.UiSection;

/** "Messages" screen: contact/thread list, per the canonical prototype's Messages screen. */
public final class MessagesActivity extends UiListScreenActivity {
    @Override protected String screenTitle() { return "MESSAGES"; }
    @Override protected UiSection section() { return UiSection.MESSAGES; }
    @Override protected String itemLabel() { return "contact"; }
    @Override protected String detailsHint() { return "Last message / number"; }
}
