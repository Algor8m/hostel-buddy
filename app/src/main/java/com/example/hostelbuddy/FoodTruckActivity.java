package com.example.hostelbuddy;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Campus Cravings food truck menu display matching the NMIMS UI inspiration.
 * Supports live Veg / Non-Veg filtering and item availability indicators.
 */
public class FoodTruckActivity extends AppCompatActivity {

    private TextView txtCravingsTimings, txtCravingsVenue, cravingsEmptyText;
    private TextView filterAll, filterVegText, filterNonVegText;
    private View filterVeg, filterNonVeg;
    private LinearLayout cravingsItemsContainer;

    private ListenerRegistration itemsListener;
    private ListenerRegistration metaListener;

    private final List<DocumentSnapshot> allItems = new ArrayList<>();
    private String selectedFilter = "ALL"; // "ALL", "VEG", "NON VEG"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_truck);

        findViewById(R.id.cravingsBackButton).setOnClickListener(v -> finish());

        txtCravingsTimings = findViewById(R.id.txtCravingsTimings);
        txtCravingsVenue = findViewById(R.id.txtCravingsVenue);
        cravingsEmptyText = findViewById(R.id.cravingsEmptyText);
        cravingsItemsContainer = findViewById(R.id.cravingsItemsContainer);

        filterAll = findViewById(R.id.filterAll);
        filterVeg = findViewById(R.id.filterVeg);
        filterVegText = findViewById(R.id.filterVegText);
        filterNonVeg = findViewById(R.id.filterNonVeg);
        filterNonVegText = findViewById(R.id.filterNonVegText);

        filterAll.setOnClickListener(v -> setFilter("ALL"));
        filterVeg.setOnClickListener(v -> setFilter("VEG"));
        filterNonVeg.setOnClickListener(v -> setFilter("NON VEG"));

        listenToMetaAndItems();
    }

    private void setFilter(String filter) {
        selectedFilter = filter;
        updateFilterTabs();
        renderFilteredItems();
    }

    private void updateFilterTabs() {
        if ("ALL".equals(selectedFilter)) {
            filterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7A0019")));
            filterAll.setTextColor(Color.WHITE);

            filterVeg.setBackgroundTintList(null);
            filterNonVeg.setBackgroundTintList(null);
        } else if ("VEG".equals(selectedFilter)) {
            filterAll.setBackgroundTintList(null);
            filterAll.setTextColor(Color.parseColor("#707070"));

            filterVeg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            filterNonVeg.setBackgroundTintList(null);
        } else if ("NON VEG".equals(selectedFilter)) {
            filterAll.setBackgroundTintList(null);
            filterAll.setTextColor(Color.parseColor("#707070"));

            filterVeg.setBackgroundTintList(null);
            filterNonVeg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
        }
    }

    private void listenToMetaAndItems() {
        metaListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_FOOD_TRUCK_META)
            .document(FirestoreConfig.DOC_FOOD_TRUCK_SETTINGS)
            .addSnapshotListener((snapshot, error) -> {
                if (snapshot != null && snapshot.exists()) {
                    String timings = snapshot.getString("timings");
                    String venue = snapshot.getString("venue");
                    if (timings != null && !timings.trim().isEmpty()) {
                        txtCravingsTimings.setText("Food Truck Hours: " + timings);
                    }
                    if (venue != null && !venue.trim().isEmpty()) {
                        txtCravingsVenue.setText("Location: " + venue);
                    }
                }
            });

        itemsListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_FOOD_TRUCK_ITEMS)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    cravingsEmptyText.setText("Could not load menu: " + error.getMessage());
                    cravingsEmptyText.setVisibility(View.VISIBLE);
                    return;
                }

                allItems.clear();
                if (snapshots != null && !snapshots.isEmpty()) {
                    allItems.addAll(snapshots.getDocuments());
                }

                renderFilteredItems();
            });
    }

    private void renderFilteredItems() {
        cravingsItemsContainer.removeAllViews();

        List<DocumentSnapshot> filtered = new ArrayList<>();
        for (DocumentSnapshot doc : allItems) {
            String cat = doc.getString("category");
            if (cat == null) cat = "Veg";

            if ("VEG".equals(selectedFilter) && !cat.equalsIgnoreCase("Veg")) {
                continue;
            }
            if ("NON VEG".equals(selectedFilter) && !cat.equalsIgnoreCase("Non Veg")) {
                continue;
            }
            filtered.add(doc);
        }

        if (filtered.isEmpty()) {
            cravingsEmptyText.setVisibility(View.VISIBLE);
            cravingsEmptyText.setText("No " + ("ALL".equals(selectedFilter) ? "" : selectedFilter + " ") +
                    "Campus Cravings items found.");
            cravingsItemsContainer.addView(cravingsEmptyText);
            return;
        }

        cravingsEmptyText.setVisibility(View.GONE);
        for (DocumentSnapshot doc : filtered) {
            cravingsItemsContainer.addView(createItemCard(doc));
        }
    }

    private View createItemCard(DocumentSnapshot doc) {
        String name = doc.getString("name");
        Double price = doc.getDouble("price");
        String category = doc.getString("category");
        Boolean available = doc.getBoolean("available");
        boolean isAvailable = available == null || available;
        boolean isVeg = category == null || category.equalsIgnoreCase("Veg");

        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        // Veg / Non-Veg Indicator Circle
        View indicator = new View(this);
        LinearLayout.LayoutParams indParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        indParams.setMargins(0, 0, dp(14), 0);
        indicator.setLayoutParams(indParams);
        indicator.setBackgroundResource(R.drawable.bg_stepper_btn);
        indicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(isVeg ? "#2E7D32" : "#C62828")));
        card.addView(indicator);

        // Name and Category Subtitle
        LinearLayout textContainer = new LinearLayout(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textContainer.setLayoutParams(textParams);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "Campus Item");
        tvName.setTextColor(Color.parseColor("#252525"));
        tvName.setTextSize(15);
        tvName.setTypeface(null, Typeface.BOLD);
        textContainer.addView(tvName);

        TextView tvCategory = new TextView(this);
        tvCategory.setText(category != null ? category : "Campus Cravings");
        tvCategory.setTextColor(Color.parseColor("#707070"));
        tvCategory.setTextSize(12);
        tvCategory.setPadding(0, dp(2), 0, 0);
        textContainer.addView(tvCategory);

        if (!isAvailable) {
            TextView tvSoldOut = new TextView(this);
            tvSoldOut.setText("Sold Out");
            tvSoldOut.setTextColor(Color.parseColor("#C62828"));
            tvSoldOut.setTextSize(11);
            tvSoldOut.setTypeface(null, Typeface.BOLD);
            tvSoldOut.setPadding(0, dp(2), 0, 0);
            textContainer.addView(tvSoldOut);
        }

        card.addView(textContainer);

        // Price Text
        TextView tvPrice = new TextView(this);
        tvPrice.setText("₹" + (price != null ? price.intValue() : 0));
        tvPrice.setTextColor(Color.parseColor("#252525"));
        tvPrice.setTextSize(16);
        tvPrice.setTypeface(null, Typeface.BOLD);
        card.addView(tvPrice);

        return card;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (itemsListener != null) itemsListener.remove();
        if (metaListener != null) metaListener.remove();
        super.onDestroy();
    }
}
