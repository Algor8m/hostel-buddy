package com.example.hostelbuddy;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SnapshotMetadata;

/**
 * Diagnostic utility for diagnosing physical device and emulator Firebase issues.
 * Differentiates network, auth, security rules, and cache-synchronization failures without
 * logging passwords, tokens, or sensitive credentials.
 */
public final class FirebaseDiagnosticHelper {

    public static final String TAG = "HostelBuddyDiag";

    private FirebaseDiagnosticHelper() { }

    public static void logFirebaseInitialization(Context context) {
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            FirebaseOptions options = app.getOptions();
            Log.i(TAG, "==== FIREBASE DIAGNOSTIC CHECK ====");
            Log.i(TAG, "App name: " + app.getName());
            Log.i(TAG, "Project ID: " + options.getProjectId());
            Log.i(TAG, "Application ID: " + options.getApplicationId());
            Log.i(TAG, "Package name: " + context.getPackageName());
            Log.i(TAG, "Persistence enabled: true");
            Log.i(TAG, "Network connected: " + NetworkMonitor.getInstance(context).isConnected());
            Log.i(TAG, "===================================");
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Firebase default app is NOT initialized: " + e.getMessage());
        }
    }

    public static String classifyError(@Nullable Context context, @Nullable Exception exception, @NonNull String operationContext) {
        if (exception == null) {
            return "Operation failed due to an unknown issue.";
        }

        // 1. Check physical device network connectivity first
        if (context != null && !NetworkMonitor.getInstance(context).isConnected()) {
            Log.w(TAG, operationContext + ": Physical device has NO active internet connection.");
            return "No Internet Connection: Your device appears to be offline. Please check your Wi-Fi or mobile data.";
        }

        // 2. Check Firestore-specific exception codes
        if (exception instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException fe = (FirebaseFirestoreException) exception;
            FirebaseFirestoreException.Code code = fe.getCode();
            Log.w(TAG, operationContext + " Firestore Error Code: " + code + " - " + fe.getMessage());

            switch (code) {
                case PERMISSION_DENIED:
                    return "Access Denied: Cloud Firestore Security Rules rejected this request. Please verify that your account has the required role.";
                case UNAUTHENTICATED:
                    return "Session Expired: Please log in again to authenticate your request.";
                case NOT_FOUND:
                    return "Not Found: The requested Firestore record does not exist on the server.";
                case UNAVAILABLE:
                    return "Service Temporarily Unavailable: Cloud Firestore servers could not be reached. Will retry automatically.";
                case DEADLINE_EXCEEDED:
                    return "Request Timed Out: The network connection to Firebase was too slow. Please retry.";
                case RESOURCE_EXHAUSTED:
                    return "Quota Exceeded: Firestore project quota or rate limits reached.";
                default:
                    return "Firestore Error (" + code + "): " + fe.getLocalizedMessage();
            }
        }

        // 3. Check Authentication-specific exception codes
        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException ae = (FirebaseAuthException) exception;
            String errorCode = ae.getErrorCode();
            Log.w(TAG, operationContext + " Auth Error: " + errorCode);

            if ("ERROR_INVALID_CREDENTIAL".equals(errorCode) || "ERROR_WRONG_PASSWORD".equals(errorCode) || "ERROR_USER_NOT_FOUND".equals(errorCode)) {
                return "Invalid Credentials: Email or password is incorrect.";
            } else if ("ERROR_USER_DISABLED".equals(errorCode)) {
                return "Account Disabled: This account has been disabled by an administrator.";
            } else if ("ERROR_TOO_MANY_REQUESTS".equals(errorCode)) {
                return "Too Many Attempts: Access temporarily blocked. Please wait a few moments before retrying.";
            } else if ("ERROR_OPERATION_NOT_ALLOWED".equals(errorCode)) {
                return "Authentication Not Enabled: Email/Password provider is disabled in Firebase Console.";
            }
        }

        String message = exception.getMessage() != null ? exception.getMessage() : "";
        String lower = message.toLowerCase();

        if (lower.contains("failed to get document because the client is offline")) {
            return "Offline: Document is not cached locally and the device cannot reach Firebase.";
        }
        if (lower.contains("network")) {
            return "Network Error: Could not reach Firebase servers. Please verify your connection.";
        }

        Log.w(TAG, operationContext + " General Error: " + message);
        return message.isEmpty() ? "Operation failed. Please try again." : message;
    }

    public static String getSourceDescription(@Nullable SnapshotMetadata metadata) {
        if (metadata == null) return "Unknown";
        boolean isCache = metadata.isFromCache();
        boolean hasPendingWrites = metadata.hasPendingWrites();

        if (isCache && hasPendingWrites) {
            return "Local (Pending sync to server)";
        } else if (isCache) {
            return "Local cache (Offline)";
        } else {
            return "Live (Confirmed from server)";
        }
    }
}
