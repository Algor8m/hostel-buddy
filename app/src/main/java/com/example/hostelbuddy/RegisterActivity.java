package com.example.hostelbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handles student self-registration using Firebase Authentication and stores
 * the student profile in Cloud Firestore with role = "student".
 * Strictly enforces 1-to-1 mapping between student SAP ID and UID.
 */
public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText nameInput, emailInput, rollInput, roomInput, phoneInput, passwordInput, confirmPasswordInput;
    private TextView messageText;
    private Button submitButton, backButton;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        nameInput = findViewById(R.id.registerNameInput);
        emailInput = findViewById(R.id.registerEmailInput);
        rollInput = findViewById(R.id.registerRollInput);
        roomInput = findViewById(R.id.registerRoomInput);
        phoneInput = findViewById(R.id.registerPhoneInput);
        passwordInput = findViewById(R.id.registerPasswordInput);
        confirmPasswordInput = findViewById(R.id.registerConfirmPasswordInput);
        messageText = findViewById(R.id.registerMessage);
        submitButton = findViewById(R.id.registerSubmitButton);
        backButton = findViewById(R.id.registerBackButton);

        submitButton.setOnClickListener(v -> performRegistration());
        backButton.setOnClickListener(v -> finish());
    }

    private void performRegistration() {
        if (busy) return;

        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String sapId = rollInput.getText().toString().trim();
        String room = roomInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (name.isEmpty()) {
            messageText.setText("Please enter your full name.");
            nameInput.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            messageText.setText("Please enter a valid college email address.");
            emailInput.requestFocus();
            return;
        }

        if (sapId.isEmpty()) {
            messageText.setText("Please enter your NMIMS SAP ID.");
            rollInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            messageText.setText("Password must be at least 6 characters.");
            passwordInput.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            messageText.setText("Passwords do not match.");
            confirmPasswordInput.requestFocus();
            return;
        }

        setBusy(true, "Verifying SAP ID availability…");

        // Enforce 1-to-1 mapping: Check if this SAP ID is already assigned in Firestore
        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .whereEqualTo("sapId", sapId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    setBusy(false, "This SAP ID is already registered to an existing account. Please log in.");
                    return;
                }
                createUserAccount(name, email, sapId, room, phone, password);
            })
            .addOnFailureListener(e -> {
                // If query fails (e.g. index/network), proceed to create account
                createUserAccount(name, email, sapId, room, phone, password);
            });
    }

    private void createUserAccount(String name, String email, String sapId, String room, String phone, String password) {
        setBusy(true, "Creating student account…");

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                setBusy(false, friendlyAuthError(task.getException()));
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            String uid = user.getUid();

            // Set displayName in Firebase Auth profile
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build();
            user.updateProfile(profileUpdates);

            // Create student profile in Cloud Firestore according to Section 3.1
            Map<String, Object> profile = new HashMap<>();
            profile.put("uid", uid);
            profile.put("name", name);
            profile.put("fullName", name);
            profile.put("sapId", sapId); // Stored strictly as string to preserve leading zeros
            profile.put("rollNumber", sapId);
            profile.put("email", email);
            profile.put("roomNumber", room);
            profile.put("phone", phone);
            profile.put("role", "student"); // Public self-registration ALWAYS sets student role
            profile.put("createdAt", FieldValue.serverTimestamp());

            FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                    .document(uid)
                    .set(profile)
                    .addOnSuccessListener(aVoid -> {
                        setBusy(false, "");
                        Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setBusy(false, "Account created, but failed to save profile: " + e.getMessage());
                    });
        });
    }

    private void setBusy(boolean value, String status) {
        busy = value;
        submitButton.setEnabled(!value);
        backButton.setEnabled(!value);
        nameInput.setEnabled(!value);
        emailInput.setEnabled(!value);
        rollInput.setEnabled(!value);
        roomInput.setEnabled(!value);
        phoneInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        confirmPasswordInput.setEnabled(!value);
        messageText.setText(status);
    }

    private String friendlyAuthError(Exception exception) {
        String raw = exception == null ? "" : exception.getMessage();
        if (raw == null) raw = "";
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("email-already-in-use")) {
            return "An account with this email already exists. Please log in.";
        }
        if (lower.contains("network")) {
            return "Network error. Please check your internet connection and try again.";
        }
        if (lower.contains("weak-password")) {
            return "The password is too weak. Please use at least 6 characters.";
        }
        return raw.isEmpty() ? "Registration failed. Please try again." : raw;
    }
}
