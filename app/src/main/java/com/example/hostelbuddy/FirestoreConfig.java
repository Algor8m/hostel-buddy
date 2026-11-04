package com.example.hostelbuddy;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

/**
 * Central Firebase Firestore configuration and collection constants for Hostel Buddy.
 */
public final class FirestoreConfig {

    private static final String TAG = "FirestoreConfig";

    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_MESS_MENU = "messMenu";
    public static final String COLLECTION_MESS_MENU_META = "messMenuMeta";
    public static final String DOC_ACTIVE_MENU = "activeMenu";
    public static final String COLLECTION_FOOD_TRUCK_ITEMS = "foodTruckItems";
    public static final String COLLECTION_FOOD_TRUCK_META = "foodTruckMeta";
    public static final String DOC_FOOD_TRUCK_SETTINGS = "settings";
    public static final String COLLECTION_LAUNDRY_RATES = "laundryRates";
    public static final String DOC_LAUNDRY_DEFAULT_RATES = "default";
    public static final String COLLECTION_LAUNDRY_PRICES = "laundryPrices";
    public static final String COLLECTION_LAUNDRY_CONFIG = "laundryConfig";
    public static final String DOC_LAUNDRY_PRICING = "pricing";
    public static final String COLLECTION_LAUNDRY_ORDERS = "laundryOrders";
    public static final String COLLECTION_LAUNDRY_REQUESTS = "laundryRequests";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";

    private static volatile FirebaseFirestore firestoreInstance;

    private FirestoreConfig() { }

    public static synchronized FirebaseFirestore getFirestore() {
        if (firestoreInstance == null) {
            firestoreInstance = FirebaseFirestore.getInstance();
            try {
                FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build();
                firestoreInstance.setFirestoreSettings(settings);
            } catch (IllegalStateException e) {
                // Settings were already locked by application startup; safe to proceed
                Log.d(TAG, "Firestore settings already applied.");
            }
        }
        return firestoreInstance;
    }
}
