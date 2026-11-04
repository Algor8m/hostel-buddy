package com.example.hostelbuddy;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * FCM Token registration and notification logging helper.
 * Never exposes server credentials on the client.
 */
public final class FcmHelper {

    private static final String TAG = "FcmHelper";

    private FcmHelper() { }

    /**
     * Retrieves the current device registration token and syncs it to the user's Firestore document.
     */
    public static void retrieveAndSyncToken(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token != null && !token.isEmpty()) {
                        syncTokenToFirestore(user.getUid(), token);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to retrieve FCM registration token", e));
    }

    /**
     * Saves the device FCM token to users/{uid} for targeted notifications.
     */
    public static void syncTokenToFirestore(String uid, String token) {
        if (uid == null || token == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);
        data.put("lastTokenSync", FieldValue.serverTimestamp());

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token synced successfully for " + uid))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to sync FCM token", e));
    }

    /**
     * Convenience overload for recordNotification that generates a deterministic notification ID.
     */
    public static void recordNotification(String recipientUid, String title, String body, String type, String referenceId) {
        String notifId = "NOTIF-" + (type != null ? type : "gen") + "-" + (referenceId != null ? referenceId : String.valueOf(System.currentTimeMillis()));
        recordNotification(recipientUid, notifId, title, body, type, referenceId);
    }

    /**
     * Records an in-app notification document under users/{recipientUid}/notifications.
     * Prevents duplicate notifications by using a deterministic notification ID.
     */
    public static void recordNotification(String recipientUid, String notificationId, String title, String body, String type, String referenceId) {
        if (recipientUid == null || recipientUid.isEmpty() || notificationId == null) return;

        Map<String, Object> notif = new HashMap<>();
        notif.put("notificationId", notificationId);
        notif.put("title", title);
        notif.put("body", body);
        notif.put("type", type);
        notif.put("referenceId", referenceId);
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                .document(recipientUid)
                .collection(FirestoreConfig.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .set(notif, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Could not write in-app notification", e));
    }
}
