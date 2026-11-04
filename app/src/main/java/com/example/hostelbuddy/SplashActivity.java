package com.example.hostelbuddy;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private ImageView splashLogo;
    private TextView splashTitle, splashTagline;
    private ProgressBar splashProgressBar;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long MIN_SPLASH_DURATION_MS = 1300L;
    private long startTimeMs = 0L;
    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashLogo = findViewById(R.id.splashLogo);
        splashTitle = findViewById(R.id.splashTitle);
        splashTagline = findViewById(R.id.splashTagline);
        splashProgressBar = findViewById(R.id.splashProgressBar);

        // Initial invisible state for animation
        splashLogo.setAlpha(0f);
        splashLogo.setScaleX(0.82f);
        splashLogo.setScaleY(0.82f);

        splashTitle.setAlpha(0f);
        splashTitle.setTranslationY(30f);

        splashTagline.setAlpha(0f);
        splashTagline.setTranslationY(20f);

        splashProgressBar.setAlpha(0f);

        startTimeMs = System.currentTimeMillis();
        startSplashAnimation();
        resolveDestination();
    }

    private void startSplashAnimation() {
        // Logo animation: Fade & gentle scale up
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(splashLogo, View.ALPHA, 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(splashLogo, View.SCALE_X, 0.82f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(splashLogo, View.SCALE_Y, 0.82f, 1f);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        logoSet.setDuration(700);
        logoSet.setInterpolator(new DecelerateInterpolator());

        // Title animation: Fade & upward slide
        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(splashTitle, View.ALPHA, 0f, 1f);
        ObjectAnimator titleTrans = ObjectAnimator.ofFloat(splashTitle, View.TRANSLATION_Y, 30f, 0f);
        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(titleAlpha, titleTrans);
        titleSet.setDuration(500);
        titleSet.setStartDelay(350);
        titleSet.setInterpolator(new DecelerateInterpolator());

        // Tagline animation: Fade & upward slide
        ObjectAnimator tagAlpha = ObjectAnimator.ofFloat(splashTagline, View.ALPHA, 0f, 1f);
        ObjectAnimator tagTrans = ObjectAnimator.ofFloat(splashTagline, View.TRANSLATION_Y, 20f, 0f);
        AnimatorSet tagSet = new AnimatorSet();
        tagSet.playTogether(tagAlpha, tagTrans);
        tagSet.setDuration(500);
        tagSet.setStartDelay(550);
        tagSet.setInterpolator(new DecelerateInterpolator());

        // Progress bar subtle reveal
        ObjectAnimator progressAlpha = ObjectAnimator.ofFloat(splashProgressBar, View.ALPHA, 0f, 1f);
        progressAlpha.setDuration(400);
        progressAlpha.setStartDelay(800);

        AnimatorSet masterSet = new AnimatorSet();
        masterSet.playTogether(logoSet, titleSet, tagSet, progressAlpha);
        masterSet.start();
    }

    private void resolveDestination() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            navigateAfterDelay(new Intent(SplashActivity.this, LoginActivity.class));
            return;
        }

        // Verify user role asynchronously from Firestore
        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .document(currentUser.getUid())
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String role = snapshot.getString("role");
                    if (role != null && !role.trim().isEmpty()) {
                        routeForRole(LoginActivity.normalizeRole(role));
                        return;
                    }
                }
                // Fallback to RTDB
                checkLegacyRole(currentUser);
            })
            .addOnFailureListener(e -> checkLegacyRole(currentUser));
    }

    private void checkLegacyRole(FirebaseUser currentUser) {
        DatabaseConfig.getDatabase().getReference("users").child(currentUser.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String legacyRole = LoginActivity.extractRoleFromSnapshot(snapshot);
                    if (legacyRole != null && !legacyRole.trim().isEmpty()) {
                        routeForRole(LoginActivity.normalizeRole(legacyRole));
                    } else {
                        // User without role -> route to Login
                        navigateAfterDelay(new Intent(SplashActivity.this, LoginActivity.class));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    navigateAfterDelay(new Intent(SplashActivity.this, LoginActivity.class));
                }
            });
    }

    private void routeForRole(String role) {
        Intent destination;
        if ("student".equals(role)) {
            destination = new Intent(SplashActivity.this, DashboardActivity.class);
        } else if ("laundry_admin".equals(role)) {
            destination = new Intent(SplashActivity.this, LaundryAdminActivity.class);
        } else if ("mess_admin".equals(role) || "menu_admin".equals(role)) {
            destination = new Intent(SplashActivity.this, MessAdminActivity.class);
        } else if ("food_truck_admin".equals(role) || "cc_admin".equals(role)) {
            destination = new Intent(SplashActivity.this, CampusCravingsAdminActivity.class);
        } else {
            destination = new Intent(SplashActivity.this, AdminDashboardActivity.class);
            destination.putExtra("role", role);
        }
        navigateAfterDelay(destination);
    }

    private void navigateAfterDelay(Intent targetIntent) {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        long remaining = Math.max(0L, MIN_SPLASH_DURATION_MS - elapsed);

        mainHandler.postDelayed(() -> {
            if (isNavigating || isFinishing()) return;
            isNavigating = true;

            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(targetIntent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, remaining);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
