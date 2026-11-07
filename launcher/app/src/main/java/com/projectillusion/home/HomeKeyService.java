package com.projectillusion.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * For remotes whose Home button sends KEYCODE_ALL_APPS (Xiaomi sticks and boxes): Android routes that key
 * only to the built-in launcher, so once it is switched off the button would do nothing. This sends it
 * to Project Illusion. Every other key passes through. Opt-in: HOME_KEY_SERVICE=1 build-and-install.sh.
 */
public class HomeKeyService extends AccessibilityService {
    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
