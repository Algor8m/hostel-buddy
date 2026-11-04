package com.example.hostelbuddy;

import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Centralized, secure logout helper for Hostel Buddy.
 * Guarantees:
 * 1. Immediate termination of active Firebase Authentication session via FirebaseAuth.getInstance().signOut().
 * 2. Complete clearing of the activity back stack using Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK.
 * 3. Clean navigation to LoginActivity so pressing the system Back button cannot reopen authenticated screens.
 * 4. User confirmation dialog before signing out to prevent accidental disruptions.
 * 5. Preservation of all user accounts, profiles, Firestore collections, and saved orders.
 */
public final class LogoutHelper {

    private LogoutHelper() { }

    /**
     * Shows a confirmation dialog and signs out the user upon positive confirmation.
     *
     * @param activity The host activity initiating logout.
     */
    public static void confirmAndSignOut(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        new AlertDialog.Builder(activity)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out of your session?")
            .setPositiveButton("Logout", (dialog, which) -> performSignOut(activity))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Unconditionally signs out of Firebase, clears the task back stack, and opens LoginActivity.
     *
     * @param activity The host activity to terminate.
     */
    public static void performSignOut(Activity activity) {
        if (activity == null) return;

        // 1. Sign out from Firebase Authentication
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) { }

        // 2 & 3. Reset back stack and navigate to LoginActivity
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
