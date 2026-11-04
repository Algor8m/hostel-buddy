package com.example.hostelbuddy;

import com.google.firebase.database.FirebaseDatabase;

/** Central Firebase Realtime Database configuration for Hostel Buddy. */
public final class DatabaseConfig {
    public static final String DATABASE_URL =
            "https://hostel-buddy-ar12-default-rtdb.asia-southeast1.firebasedatabase.app";

    private DatabaseConfig() { }

    public static FirebaseDatabase getDatabase() {
        return FirebaseDatabase.getInstance(DATABASE_URL);
    }
}
