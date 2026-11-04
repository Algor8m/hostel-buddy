package com.example.hostelbuddy;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/**
 * Modern Admin Landing page matching the NMIMS UI inspiration:
 * - Real-time Mess Now Serving preview card
 * - Action tiles for Mess Menu, Special Dinners, Campus Cravings, and Laundry
 * - Strict role gating
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private String role = "";
    private TextView title, description;
    private View manageMessCard, specialDinnerCard, manageCravingsCard, manageLaundryCard;
    private View nowServingCard;
    private TextView adminServingBadge, adminServingTitle, adminServingItems, adminServingTiming;

    private MessMenuRepository.NowServingListener nowServingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard);
        title = findViewById(R.id.adminWelcome);
        description = findViewById(R.id.adminDescription);

        manageMessCard = findViewById(R.id.manageMessCard);
        specialDinnerCard = findViewById(R.id.specialDinnerCard);
        manageCravingsCard = findViewById(R.id.manageCravingsCard);
        manageLaundryCard = findViewById(R.id.manageLaundryCard);

        nowServingCard = findViewById(R.id.adminNowServingCard);
        adminServingBadge = findViewById(R.id.adminServingBadge);
        adminServingTitle = findViewById(R.id.adminServingTitle);
        adminServingItems = findViewById(R.id.adminServingItems);
        adminServingTiming = findViewById(R.id.adminServingTiming);

        findViewById(R.id.adminBackIcon).setOnClickListener(v -> finish());

        View.OnClickListener logoutClick = v -> LogoutHelper.confirmAndSignOut(this);

        Button logout = findViewById(R.id.adminLogoutButton);
        if (logout != null) logout.setOnClickListener(logoutClick);
        View headerLogout = findViewById(R.id.adminHeaderLogout);
        if (headerLogout != null) headerLogout.setOnClickListener(logoutClick);

        role = getIntent().getStringExtra("role");
        if (role == null || role.trim().isEmpty()) {
            FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && snapshot.getString("role") != null) {
                        role = snapshot.getString("role");
                        configureDashboardForRole();
                    } else {
                        fetchLegacyRoleFromRtdb(currentUser);
                    }
                })
                .addOnFailureListener(e -> fetchLegacyRoleFromRtdb(currentUser));
        } else {
            configureDashboardForRole();
        }

        setupNowServingObserver();
    }

    private void fetchLegacyRoleFromRtdb(FirebaseUser currentUser) {
        DatabaseConfig.getDatabase().getReference("users").child(currentUser.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String legacyRole = LoginActivity.extractRoleFromSnapshot(snapshot);
                    if (legacyRole != null && !legacyRole.trim().isEmpty()) {
                        role = LoginActivity.normalizeRole(legacyRole);
                    }
                    configureDashboardForRole();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    configureDashboardForRole();
                }
            });
    }

    private void setupNowServingObserver() {
        nowServingListener = new MessMenuRepository.NowServingListener() {
            @Override
            public void onNowServingUpdated(@NonNull NowServingService.NowServingInfo info) {
                adminServingTitle.setText(info.mealName + " • " + info.servingHours);
                adminServingItems.setText(formatBulletPoints(info.foodItems));
                adminServingTiming.setText("Venue: " + info.venue + " • " + info.timeStatusText);

                if (info.isNowServing()) {
                    adminServingBadge.setText("MESS NOW SERVING (LIVE)");
                    adminServingBadge.setTextColor(Color.parseColor("#0E6251"));
                } else if (info.isFinished()) {
                    adminServingBadge.setText("SERVING CONCLUDED");
                    adminServingBadge.setTextColor(Color.parseColor("#707070"));
                } else {
                    adminServingBadge.setText("UPCOMING MEAL");
                    adminServingBadge.setTextColor(Color.parseColor("#E67E22"));
                }
            }

            @Override
            public void onMenuError(@NonNull String errorMessage) {
                adminServingItems.setText("Could not load serving data: " + errorMessage);
            }
        };
        MessMenuRepository.getInstance().addListener(nowServingListener);
    }

    private String formatBulletPoints(String raw) {
        if (raw == null || raw.trim().isEmpty() || "-".equals(raw.trim())) {
            return "• No items configured for this meal.";
        }
        String[] parts = raw.split("[,;\\n]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(trimmed);
            }
        }
        return sb.length() > 0 ? sb.toString() : "• " + raw;
    }

    private void configureDashboardForRole() {
        if ("student".equals(role)) {
            // Students are strictly denied access to Admin Dashboard (Section 1.2)
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        if ("laundry_admin".equals(role)) {
            title.setText("WELCOME LAUNDRY ADMIN");
            description.setText("Intake student laundry orders, update processing status, and configure rates.");
            manageMessCard.setVisibility(View.GONE);
            specialDinnerCard.setVisibility(View.GONE);
            manageCravingsCard.setVisibility(View.GONE);
            nowServingCard.setVisibility(View.GONE);

            manageLaundryCard.setVisibility(View.VISIBLE);
            manageLaundryCard.setOnClickListener(v ->
                startActivity(new Intent(this, LaundryAdminActivity.class)));

        } else if ("food_truck_admin".equals(role) || "cc_admin".equals(role)) {
            title.setText("WELCOME CC ADMIN");
            description.setText("Manage Campus Cravings menu items, pricing, and live availability.");
            manageMessCard.setVisibility(View.GONE);
            specialDinnerCard.setVisibility(View.GONE);
            manageLaundryCard.setVisibility(View.GONE);
            nowServingCard.setVisibility(View.GONE);

            manageCravingsCard.setVisibility(View.VISIBLE);
            manageCravingsCard.setOnClickListener(v ->
                startActivity(new Intent(this, CampusCravingsAdminActivity.class)));

        } else if ("mess_admin".equals(role) || "menu_admin".equals(role)) {
            title.setText("WELCOME MESS ADMIN");
            description.setText("Manage rotating mess menu, special dinners, and meal timings.");
            manageLaundryCard.setVisibility(View.GONE);
            manageCravingsCard.setVisibility(View.GONE);
            manageMessCard.setVisibility(View.VISIBLE);
            specialDinnerCard.setVisibility(View.VISIBLE);
            nowServingCard.setVisibility(View.VISIBLE);

            manageMessCard.setOnClickListener(v ->
                startActivity(new Intent(this, MessAdminActivity.class)));
            specialDinnerCard.setOnClickListener(v ->
                startActivity(new Intent(this, MessAdminActivity.class)));

        } else {
            // Super admin or multi-role
            title.setText("WELCOME SUPER ADMIN");
            description.setText("Manage rotating mess menu, special dinners, Campus Cravings, and Laundry.");
            manageLaundryCard.setVisibility(View.VISIBLE);
            manageMessCard.setVisibility(View.VISIBLE);
            specialDinnerCard.setVisibility(View.VISIBLE);
            manageCravingsCard.setVisibility(View.VISIBLE);
            nowServingCard.setVisibility(View.VISIBLE);

            manageMessCard.setOnClickListener(v ->
                startActivity(new Intent(this, MessAdminActivity.class)));
            specialDinnerCard.setOnClickListener(v ->
                startActivity(new Intent(this, MessAdminActivity.class)));
            manageCravingsCard.setOnClickListener(v ->
                startActivity(new Intent(this, CampusCravingsAdminActivity.class)));
            manageLaundryCard.setOnClickListener(v ->
                startActivity(new Intent(this, LaundryAdminActivity.class)));
        }
    }

    @Override
    protected void onDestroy() {
        if (nowServingListener != null) {
            MessMenuRepository.getInstance().removeListener(nowServingListener);
        }
        super.onDestroy();
    }

    private void goToLogin() {
        LogoutHelper.performSignOut(this);
    }
}
