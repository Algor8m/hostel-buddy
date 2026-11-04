package com.example.hostelbuddy;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public class HostelBuddyApp extends Application {

    private static final String TAG = "HostelBuddyApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Initialize Network Monitor
        NetworkMonitor.getInstance(this);

        // 2. Ensure Firebase is initialized
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
            Log.i(TAG, "Firebase initialized via HostelBuddyApp.");
        }

        // 3. Configure Firestore settings once globally
        try {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build();
            firestore.setFirestoreSettings(settings);
            Log.i(TAG, "Firestore persistent cache configured successfully.");
        } catch (IllegalStateException e) {
            // Already initialized, harmless to ignore
            Log.d(TAG, "Firestore already configured: " + e.getMessage());
        }

        // 4. Log diagnostic state for verification
        FirebaseDiagnosticHelper.logFirebaseInitialization(this);
    }
}
