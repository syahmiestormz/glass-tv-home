package com.syahmie.stormztv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Catches the remote's app buttons (see {@link RemoteButtons}) and opens the app the owner chose for
 * each. It only looks at key presses, and only the four app buttons: everything else passes straight
 * through, and a button with no app chosen keeps doing what the box does. It reads no screen content.
 * The owner turns it on once: Settings → Accessibility → Stormz TV Buttons.
 */
public class ButtonService extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!RemoteButtons.hasMapping(this, code)) return false;
        // Swallow both halves of the press; open the app once, when the button is released.
        if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
            if (!RemoteButtons.launch(this, code)) return false;
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
