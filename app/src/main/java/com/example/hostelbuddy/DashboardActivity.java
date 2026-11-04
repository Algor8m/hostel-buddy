package com.example.hostelbuddy;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class DashboardActivity extends AppCompatActivity {

    private TextView welcomeText, studentSubInfo, syncStatusBadge;
    private TextView nowServingBadge, nowServingDayLabel, nowServingMealTitle;
    private TextView nowServingHours, nowServingItems, nowServingVenue;
    private TextView specialDinnerBadge, nowServingStatusText;
    private TextView laundryUpdateStatus;
    private View btnViewMenuNow, cardLaundryUpdate, cardSpecialDinner, btnHeaderLogout;

    private MessMenuRepository.NowServingListener nowServingListener;
    private NetworkMonitor.ConnectivityListener connectivityListener;
    private ListenerRegistration laundryOrderListener;

    private String userDisplayName = "Student";
    private String userRollNumber = "70022400269";
    private String userRoomNumber = "504-A";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_dashboard);

        welcomeText = findViewById(R.id.welcomeText);
        studentSubInfo = findViewById(R.id.studentSubInfo);
        syncStatusBadge = findViewById(R.id.syncStatusBadge);

        nowServingBadge = findViewById(R.id.nowServingBadge);
        nowServingDayLabel = findViewById(R.id.nowServingDayLabel);
        nowServingMealTitle = findViewById(R.id.nowServingMealTitle);
        nowServingHours = findViewById(R.id.nowServingHours);
        nowServingItems = findViewById(R.id.nowServingItems);
        nowServingVenue = findViewById(R.id.nowServingVenue);
        specialDinnerBadge = findViewById(R.id.specialDinnerBadge);
        nowServingStatusText = findViewById(R.id.nowServingStatusText);

        laundryUpdateStatus = findViewById(R.id.laundryUpdateStatus);
        btnViewMenuNow = findViewById(R.id.btnViewMenuNow);
        cardLaundryUpdate = findViewById(R.id.cardLaundryUpdate);
        cardSpecialDinner = findViewById(R.id.cardSpecialDinner);
        btnHeaderLogout = findViewById(R.id.btnHeaderLogout);

        // Fallback display name
        String fallback = currentUser.getDisplayName();
        if (fallback == null || fallback.trim().isEmpty()) {
            String email = currentUser.getEmail();
            fallback = (email == null ? "Student" : email.split("@")[0]);
        }
        userDisplayName = fallback;
        updateHeaderGreeting();

        // Fetch student profile details from Firestore
        loadStudentProfile(currentUser);

        // Navigation click listeners
        findViewById(R.id.messButton).setOnClickListener(v ->
            startActivity(new Intent(this, MessMenuActivity.class)));
        findViewById(R.id.foodButton).setOnClickListener(v ->
            startActivity(new Intent(this, FoodTruckActivity.class)));
        findViewById(R.id.laundryButton).setOnClickListener(v ->
            startActivity(new Intent(this, LaundryActivity.class)));

        if (btnViewMenuNow != null) {
            btnViewMenuNow.setOnClickListener(v ->
                startActivity(new Intent(this, MessMenuActivity.class)));
        }
        if (cardLaundryUpdate != null) {
            cardLaundryUpdate.setOnClickListener(v ->
                startActivity(new Intent(this, LaundryActivity.class)));
        }
        if (cardSpecialDinner != null) {
            cardSpecialDinner.setOnClickListener(v ->
                startActivity(new Intent(this, MessMenuActivity.class)));
        }

        View.OnClickListener logoutClick = v -> LogoutHelper.confirmAndSignOut(this);

        Button logout = findViewById(R.id.logoutButton);
        if (logout != null) logout.setOnClickListener(logoutClick);
        if (btnHeaderLogout != null) btnHeaderLogout.setOnClickListener(logoutClick);

        // Real-time observers
        setupNowServingObserver();
        setupNetworkObserver();
        listenToStudentLaundry(currentUser.getUid());
    }

    private void updateHeaderGreeting() {
        welcomeText.setText("Hi " + userDisplayName + ", welcome back!");
        String roll = (userRollNumber != null && !userRollNumber.isEmpty()) ? userRollNumber : "Student";
        String room = (userRoomNumber != null && !userRoomNumber.isEmpty()) ? userRoomNumber : "Hostel";
        studentSubInfo.setText(roll + " • NMIMS Campus • Room " + room);
    }

    private void loadStudentProfile(FirebaseUser currentUser) {
        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .document(currentUser.getUid())
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String fn = snapshot.getString("fullName");
                    if (fn == null || fn.trim().isEmpty()) fn = snapshot.getString("name");
                    if (fn != null && !fn.trim().isEmpty()) userDisplayName = fn;
                    String roll = snapshot.getString("sapId");
                    if (roll == null || roll.trim().isEmpty()) roll = snapshot.getString("rollNumber");
                    if (roll != null && !roll.trim().isEmpty()) userRollNumber = roll;
                    String room = snapshot.getString("roomNumber");
                    if (room != null && !room.trim().isEmpty()) userRoomNumber = room;
                    updateHeaderGreeting();
                } else {
                    fetchLegacyProfile(currentUser);
                }
            })
            .addOnFailureListener(e -> fetchLegacyProfile(currentUser));
    }

    private void fetchLegacyProfile(FirebaseUser currentUser) {
        DatabaseConfig.getDatabase().getReference("users").child(currentUser.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    if (snap.exists()) {
                        String name = snap.child("fullName").getValue(String.class);
                        if (name == null) name = snap.child("name").getValue(String.class);
                        if (name != null && !name.trim().isEmpty()) userDisplayName = name;
                        String roll = snap.child("rollNumber").getValue(String.class);
                        if (roll != null) userRollNumber = roll;
                        String room = snap.child("roomNumber").getValue(String.class);
                        if (room != null) userRoomNumber = room;
                        updateHeaderGreeting();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) { }
            });
    }

    private void listenToStudentLaundry(String uid) {
        laundryOrderListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS)
            .whereEqualTo("userId", uid)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null || snapshots.isEmpty()) {
                    laundryUpdateStatus.setText("No active orders. Drop off clothes at laundry desk.");
                    return;
                }
                // Find latest active order
                LaundryOrder latestActive = null;
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                    LaundryOrder order = LaundryOrder.fromSnapshot(doc.getId(), doc.getData());
                    if (!LaundryOrder.STATUS_COMPLETED.equalsIgnoreCase(order.getStatus()) &&
                        !LaundryOrder.STATUS_CANCELLED.equalsIgnoreCase(order.getStatus())) {
                        latestActive = order;
                        break;
                    }
                }
                if (latestActive != null) {
                    int totalItems = latestActive.getWashCount() + latestActive.getIronCount() +
                            latestActive.getDryCount() + latestActive.getBedsheetCount();
                    String orderDisplay = (latestActive.getOrderId() != null && !latestActive.getOrderId().isEmpty())
                            ? "Order #" + latestActive.getOrderId() : "Current Order";
                    laundryUpdateStatus.setText(orderDisplay + " is " + latestActive.getStatus() +
                            (totalItems > 0 ? " (" + totalItems + " items)" : ""));
                } else {
                    laundryUpdateStatus.setText("Laundry collection is complete. All orders received.");
                }
            });
    }

    private void setupNowServingObserver() {
        nowServingListener = new MessMenuRepository.NowServingListener() {
            @Override
            public void onNowServingUpdated(@NonNull NowServingService.NowServingInfo info) {
                renderNowServing(info);
            }

            @Override
            public void onMenuError(@NonNull String errorMessage) {
                nowServingItems.setText("Could not load serving data: " + errorMessage);
            }
        };
        MessMenuRepository.getInstance().addListener(nowServingListener);
    }

    private void renderNowServing(NowServingService.NowServingInfo info) {
        nowServingDayLabel.setText(info.weekAndDayLabel);
        nowServingMealTitle.setText(info.mealName);
        nowServingHours.setText("Serving hours: " + info.servingHours);
        nowServingItems.setText(info.foodItems);
        nowServingVenue.setText("Venue: " + info.venue);
        nowServingStatusText.setText(info.nextMealTitle + " (" + info.timeStatusText + ")");

        if (info.isSpecialDinner) {
            specialDinnerBadge.setText("Tonight: " + info.specialDinnerItems + "  →");
        } else {
            specialDinnerBadge.setText("Explore tonight's menu  →");
        }

        switch (info.state) {
            case NOW_SERVING:
                nowServingBadge.setText("LIVE");
                nowServingBadge.setTextColor(Color.parseColor("#27AE60"));
                break;
            case UPCOMING:
                nowServingBadge.setText("UPCOMING");
                nowServingBadge.setTextColor(Color.parseColor("#E67E22"));
                break;
            case FINISHED:
                nowServingBadge.setText("CONCLUDED");
                nowServingBadge.setTextColor(Color.parseColor("#707070"));
                break;
        }
    }

    private void setupNetworkObserver() {
        connectivityListener = (isConnected, isWifi, isCellular) -> {
            if (!isConnected) {
                syncStatusBadge.setText("○ Device Offline — Showing cached data");
            } else {
                String type = isWifi ? "Wi-Fi" : (isCellular ? "Cellular" : "Internet");
                syncStatusBadge.setText("● Connected via " + type + " • Live Sync");
            }
        };
        NetworkMonitor.getInstance(this).addListener(connectivityListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MessMenuRepository.getInstance().recomputeAndNotify();
    }

    @Override
    protected void onDestroy() {
        if (nowServingListener != null) {
            MessMenuRepository.getInstance().removeListener(nowServingListener);
        }
        if (connectivityListener != null) {
            NetworkMonitor.getInstance(this).removeListener(connectivityListener);
        }
        if (laundryOrderListener != null) {
            laundryOrderListener.remove();
        }
        super.onDestroy();
    }

    private void goToLogin() {
        LogoutHelper.performSignOut(this);
    }
}
