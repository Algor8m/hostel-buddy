package com.example.hostelbuddy;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LoginActivity providing exactly two primary login options:
 * - Student Login
 * - Admin Login
 * Enforces strict role verification from Cloud Firestore (with legacy RTDB fallback)
 * and department-specific routing per Section 1 of specification.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String IT_SUPPORT_EMAIL = "hostel.helpdesk@nmims.edu";

    private FirebaseAuth auth;
    private EditText emailInput, passwordInput;
    private TextView message, loginSubtitleText;
    private Button loginButton, tabStudentLogin, tabAdminLogin;
    private View helpButton, registerButton, signUpRow;
    private ImageView passwordToggle;

    private boolean busy = false;
    private boolean isPasswordVisible = false;
    private boolean isStudentLoginMode = true; // true = Student Login, false = Admin Login

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        message = findViewById(R.id.loginMessage);
        loginSubtitleText = findViewById(R.id.loginSubtitleText);
        loginButton = findViewById(R.id.loginButton);
        tabStudentLogin = findViewById(R.id.tabStudentLogin);
        tabAdminLogin = findViewById(R.id.tabAdminLogin);
        signUpRow = findViewById(R.id.signUpRow);
        helpButton = findViewById(R.id.helpButton);
        registerButton = findViewById(R.id.registerButton);
        passwordToggle = findViewById(R.id.passwordToggle);

        tabStudentLogin.setOnClickListener(v -> setLoginMode(true));
        tabAdminLogin.setOnClickListener(v -> setLoginMode(false));

        loginButton.setOnClickListener(v -> authenticate());
        findViewById(R.id.forgotPassword).setOnClickListener(v -> sendPasswordReset());

        if (helpButton != null) {
            helpButton.setOnClickListener(v -> openHelp());
        }
        if (registerButton != null) {
            registerButton.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        }

        if (passwordToggle != null) {
            passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        }

        setLoginMode(true);
    }

    private void setLoginMode(boolean studentMode) {
        if (busy) return;
        isStudentLoginMode = studentMode;
        message.setText("");

        if (studentMode) {
            tabStudentLogin.setBackgroundResource(R.drawable.bg_primary_button);
            tabStudentLogin.setTextColor(Color.WHITE);

            tabAdminLogin.setBackground(new ColorDrawable(Color.TRANSPARENT));
            tabAdminLogin.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            loginSubtitleText.setText("Student Portal: Menu, Cravings & Laundry Tracker");
            loginButton.setText("STUDENT LOGIN");
            if (signUpRow != null) signUpRow.setVisibility(View.VISIBLE);
        } else {
            tabAdminLogin.setBackgroundResource(R.drawable.bg_primary_button);
            tabAdminLogin.setTextColor(Color.WHITE);

            tabStudentLogin.setBackground(new ColorDrawable(Color.TRANSPARENT));
            tabStudentLogin.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            loginSubtitleText.setText("Campus Administration: Laundry, Mess & Cravings Portals");
            loginButton.setText("ADMIN LOGIN");
            if (signUpRow != null) signUpRow.setVisibility(View.GONE);
        }
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordToggle.setImageResource(R.drawable.ic_visibility);
            isPasswordVisible = false;
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            passwordToggle.setImageResource(R.drawable.ic_visibility_off);
            isPasswordVisible = true;
        }
        passwordInput.setSelection(passwordInput.getText().length());
    }

    private void authenticate() {
        if (busy) return;
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            message.setText("Enter a valid college email address.");
            return;
        }
        if (password.isEmpty()) {
            message.setText("Enter your password.");
            return;
        }

        setBusy(true, "Signing in securely…");
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                setBusy(false, "");
                message.setText(friendlyAuthError(task.getException()));
                return;
            }

            FirebaseUser currentUser = auth.getCurrentUser();
            String uid = currentUser.getUid();

            // Retrieve authenticated user's trusted profile from Firestore (Section 1.2 & 1.3)
            FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String storedRole = snapshot.getString("role");
                        if (storedRole != null && !storedRole.trim().isEmpty()) {
                            processVerifiedRole(currentUser, storedRole);
                            return;
                        }
                    }
                    // Fallback to Realtime Database if Firestore document is not yet initialized
                    checkLegacyRoleForLogin(currentUser);
                })
                .addOnFailureListener(e -> checkLegacyRoleForLogin(currentUser));
        });
    }

    private void processVerifiedRole(FirebaseUser currentUser, String rawRole) {
        String normalized = normalizeRole(rawRole);

        if (isStudentLoginMode) {
            // Student Authentication (Section 1.2)
            if (!"student".equals(normalized)) {
                auth.signOut();
                setBusy(false, "This account is not registered as a Student. Please choose Admin Login.");
                return;
            }
            setBusy(false, "");
            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            // Admin Authentication (Section 1.3)
            if ("student".equals(normalized) || normalized.isEmpty()) {
                auth.signOut();
                setBusy(false, "Access denied: This account has no administrative role.");
                return;
            }

            setBusy(false, "");
            routeAdminToDepartmentDashboard(normalized);
        }
    }

    private void routeAdminToDepartmentDashboard(String normalizedRole) {
        Intent next;
        switch (normalizedRole) {
            case "laundry_admin":
                // Laundry Admin -> Laundry Dashboard (Section 1.3)
                next = new Intent(LoginActivity.this, LaundryAdminActivity.class);
                break;
            case "mess_admin":
                // Mess Admin -> Mess Dashboard
                next = new Intent(LoginActivity.this, MessAdminActivity.class);
                break;
            case "food_truck_admin":
                // CC Admin -> CC Dashboard
                next = new Intent(LoginActivity.this, CampusCravingsAdminActivity.class);
                break;
            case "menu_admin":
                // Menu Admin -> Menu Management Dashboard
                next = new Intent(LoginActivity.this, MessAdminActivity.class);
                break;
            default:
                // Super Admin or Multi-department Admin
                next = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                next.putExtra("role", normalizedRole);
                break;
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(next);
        finish();
    }

    private void checkLegacyRoleForLogin(FirebaseUser currentUser) {
        if (currentUser == null) {
            setBusy(false, "Authentication session lost. Please try again.");
            return;
        }
        String uid = currentUser.getUid();
        DatabaseConfig.getDatabase().getReference("users").child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String legacyRole = extractRoleFromSnapshot(snapshot);
                    if (legacyRole == null || legacyRole.trim().isEmpty()) {
                        auth.signOut();
                        setBusy(false, "This account has no assigned role. Contact campus administration.");
                        return;
                    }
                    String normalized = normalizeRole(legacyRole);
                    lazySyncProfileToFirestore(currentUser, normalized, snapshot);
                    processVerifiedRole(currentUser, normalized);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    auth.signOut();
                    setBusy(false, "Could not verify account role: " + error.getMessage());
                }
            });
    }

    private void lazySyncProfileToFirestore(FirebaseUser user, String normalizedRole, DataSnapshot snapshot) {
        String uid = user.getUid();
        String email = user.getEmail();
        String name = user.getDisplayName();
        String sapId = "";

        if (snapshot != null && snapshot.exists()) {
            if (snapshot.hasChild("fullName")) {
                name = snapshot.child("fullName").getValue(String.class);
            } else if (snapshot.hasChild("name")) {
                name = snapshot.child("name").getValue(String.class);
            }
            if (snapshot.hasChild("sapId")) {
                sapId = String.valueOf(snapshot.child("sapId").getValue());
            } else if (snapshot.hasChild("rollNumber")) {
                sapId = String.valueOf(snapshot.child("rollNumber").getValue());
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = (email != null && email.contains("@")) ? email.split("@")[0] : "Campus User";
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("email", email != null ? email : "");
        profile.put("name", name);
        profile.put("fullName", name);
        if (!sapId.isEmpty()) {
            profile.put("sapId", sapId);
            profile.put("rollNumber", sapId);
        }
        profile.put("role", normalizedRole);
        profile.put("createdAt", FieldValue.serverTimestamp());
        profile.put("migratedFrom", "realtime_database");

        if (snapshot != null && snapshot.exists()) {
            if (snapshot.hasChild("roomNumber")) {
                profile.put("roomNumber", snapshot.child("roomNumber").getValue(String.class));
            }
            if (snapshot.hasChild("phone")) {
                profile.put("phone", snapshot.child("phone").getValue(String.class));
            }
        }

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .document(uid)
            .set(profile, SetOptions.merge());
    }

    public static String extractRoleFromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return null;
        if (snapshot.hasChild("role")) {
            Object val = snapshot.child("role").getValue();
            return val != null ? val.toString() : null;
        }
        Object val = snapshot.getValue();
        if (val instanceof String) {
            return (String) val;
        }
        return null;
    }

    public static String normalizeRole(String role) {
        if (role == null) return "";
        String value = role.trim().toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replace(' ', '_')
                .replace('-', '_');
        if ("mess_admin".equals(value) || "mess_and_campus_cravings_admin".equals(value)) return "mess_admin";
        if ("laundry_admin".equals(value)) return "laundry_admin";
        if ("food_truck_admin".equals(value) || "campus_cravings_admin".equals(value) || "cc_admin".equals(value)) return "food_truck_admin";
        if ("menu_admin".equals(value)) return "menu_admin";
        if ("super_admin".equals(value)) return "super_admin";
        if ("student".equals(value)) return "student";
        return value;
    }

    private void sendPasswordReset() {
        String email = emailInput.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            message.setText("Enter your college email above, then tap Forgot password.");
            return;
        }
        setBusy(true, "Sending password-reset email…");
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            setBusy(false, task.isSuccessful()
                ? "If an account exists for this email, a password-reset link has been sent."
                : friendlyAuthError(task.getException()));
        });
    }

    private void openHelp() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + Uri.encode(IT_SUPPORT_EMAIL)));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Hostel Buddy - Login troubleshooting");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Hello IT Support,\n\nI need help with Hostel Buddy login.\n\nIssue:\nDevice:\n\nThank you.");
        try {
            startActivity(Intent.createChooser(emailIntent, "Contact college IT support"));
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setTitle("IT Helpdesk")
                .setMessage("Contact IT Support at:\n" + IT_SUPPORT_EMAIL)
                .setPositiveButton("OK", null)
                .show();
        }
    }

    private void setBusy(boolean value, String status) {
        busy = value;
        loginButton.setEnabled(!value);
        if (helpButton != null) helpButton.setEnabled(!value);
        if (registerButton != null) registerButton.setEnabled(!value);
        tabStudentLogin.setEnabled(!value);
        tabAdminLogin.setEnabled(!value);
        emailInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        message.setText(status);
    }

    private String friendlyAuthError(Exception exception) {
        String raw = exception == null ? "" : exception.getMessage();
        if (raw == null) raw = "";
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("no user record") || lower.contains("user-not-found") || lower.contains("invalid-credential") || lower.contains("wrong-password"))
            return "Email or password is incorrect.";
        if (lower.contains("network")) return "Network error. Check your internet connection and try again.";
        if (lower.contains("too-many-requests")) return "Too many attempts. Please wait a while before trying again.";
        if (lower.contains("operation-not-allowed")) return "Email/password sign-in is disabled in Firebase Authentication.";
        return raw.isEmpty() ? "Unable to sign in. Please try again." : raw;
    }
}
