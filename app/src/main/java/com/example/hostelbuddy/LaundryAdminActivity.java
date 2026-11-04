package com.example.hostelbuddy;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * LaundryAdminActivity - Production implementation for NMIMS Campus Laundry Management.
 * Implements Section 2 requirements:
 * - Role-secured initialization
 * - 5 Dashboard Sections: Active Orders, Ready for Collection, Completed Orders, Create Order, Order Search
 * - SAP ID verification workflow for order creation
 * - Order lifecycle state advancement with audit logging
 * - Configurable price catalog
 */
public class LaundryAdminActivity extends AppCompatActivity {

    public static final String SECTION_ACTIVE = "ACTIVE";
    public static final String SECTION_READY = "READY";
    public static final String SECTION_COMPLETED = "COMPLETED";
    public static final String SECTION_ALL = "ALL";

    private String currentSection = SECTION_ACTIVE;

    private TextView statTotalOrders, statPendingOrders, statProgressOrders, statCompletedOrders;
    private TextView adminOrdersCountLabel, adminOrdersEmptyText, adminErrorText;
    private EditText adminSearchInput;
    private Spinner statusFilterSpinner;
    private LinearLayout adminOrdersList, adminErrorContainer;
    private ProgressBar adminOrdersProgressBar;

    private Button tabActiveOrders, tabReadyOrders, tabCompletedOrders, tabAllOrders;

    private final List<LaundryOrder> allOrders = new ArrayList<>();
    private ListenerRegistration ordersListener;
    private ListenerRegistration pricingListener;

    // Supported lifecycle statuses per Section 2.4
    public static final String[] SUPPORTED_STATUSES = {
        LaundryOrder.STATUS_REGISTERED,
        LaundryOrder.STATUS_RECEIVED,
        LaundryOrder.STATUS_WASHING,
        LaundryOrder.STATUS_DRYING,
        LaundryOrder.STATUS_IRONING,
        LaundryOrder.STATUS_READY,
        LaundryOrder.STATUS_COLLECTED,
        LaundryOrder.STATUS_CANCELLED
    };

    public static final String[] FILTER_OPTIONS = {
        "All Statuses",
        LaundryOrder.STATUS_REGISTERED,
        LaundryOrder.STATUS_RECEIVED,
        LaundryOrder.STATUS_WASHING,
        LaundryOrder.STATUS_DRYING,
        LaundryOrder.STATUS_IRONING,
        LaundryOrder.STATUS_READY,
        LaundryOrder.STATUS_COLLECTED,
        LaundryOrder.STATUS_CANCELLED
    };

    // Configurable price catalog
    private final Map<String, LaundryPriceItem> priceCatalog = new LinkedHashMap<>();

    // Activity Result Launcher for ZXing QR scanning
    private final ActivityResultLauncher<ScanOptions> qrScannerLauncher = registerForActivityResult(
        new ScanContract(),
        result -> {
            if (result.getContents() != null) {
                String contents = result.getContents().trim();
                if (!contents.isEmpty()) {
                    lookupAndVerifyRequest(contents);
                }
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_laundry_admin);

        // UI Components
        findViewById(R.id.adminLaundryBack).setOnClickListener(v -> finish());
        findViewById(R.id.adminLaundryLogout).setOnClickListener(v -> LogoutHelper.confirmAndSignOut(this));
        findViewById(R.id.btnManageRates).setOnClickListener(v -> openManageRatesDialog());
        findViewById(R.id.btnCreateOrder).setOnClickListener(v -> openCreateOrderDialog());
        findViewById(R.id.btnScanLaundryQr).setOnClickListener(v -> openScanOrManualDialog());

        statTotalOrders = findViewById(R.id.statTotalOrders);
        statPendingOrders = findViewById(R.id.statPendingOrders);
        statProgressOrders = findViewById(R.id.statProgressOrders);
        statCompletedOrders = findViewById(R.id.statCompletedOrders);

        tabActiveOrders = findViewById(R.id.tabActiveOrders);
        tabReadyOrders = findViewById(R.id.tabReadyOrders);
        tabCompletedOrders = findViewById(R.id.tabCompletedOrders);
        tabAllOrders = findViewById(R.id.tabAllOrders);

        adminOrdersCountLabel = findViewById(R.id.adminOrdersCountLabel);
        adminOrdersEmptyText = findViewById(R.id.adminOrdersEmptyText);
        adminErrorContainer = findViewById(R.id.adminErrorContainer);
        adminErrorText = findViewById(R.id.adminErrorText);
        adminOrdersProgressBar = findViewById(R.id.adminOrdersProgressBar);
        findViewById(R.id.btnRetryOrders).setOnClickListener(v -> listenForOrders());

        adminSearchInput = findViewById(R.id.adminSearchInput);
        statusFilterSpinner = findViewById(R.id.statusFilterSpinner);
        adminOrdersList = findViewById(R.id.adminOrdersList);

        // Explicitly initialize section and filter spinner before section tabs trigger render (Section 2.2)
        currentSection = SECTION_ACTIVE;
        setupFilterSpinner();
        setupSectionTabs();
        initDefaultPriceCatalog();

        // Verify Laundry Admin role on initialization (Section 1.4 & 2.1)
        verifyAdminRoleAndInitialize(currentUser);
    }

    private void verifyAdminRoleAndInitialize(FirebaseUser currentUser) {
        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .document(currentUser.getUid())
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String role = snapshot.getString("role");
                    String normalized = LoginActivity.normalizeRole(role);
                    if ("laundry_admin".equals(normalized) || "super_admin".equals(normalized)) {
                        loadPricingConfig();
                        listenForOrders();
                        return;
                    }
                }
                // Unauthorized user
                denyAdminAccess();
            })
            .addOnFailureListener(e -> denyAdminAccess());
    }

    private void denyAdminAccess() {
        Toast.makeText(this, "Access Denied: Only Laundry Admins can access this portal.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void setupSectionTabs() {
        tabActiveOrders.setOnClickListener(v -> selectSection(SECTION_ACTIVE));
        tabReadyOrders.setOnClickListener(v -> selectSection(SECTION_READY));
        tabCompletedOrders.setOnClickListener(v -> selectSection(SECTION_COMPLETED));
        tabAllOrders.setOnClickListener(v -> selectSection(SECTION_ALL));
        selectSection(SECTION_ACTIVE);
    }

    private void selectSection(String section) {
        if (section == null || (!SECTION_ACTIVE.equals(section) && !SECTION_READY.equals(section)
                && !SECTION_COMPLETED.equals(section) && !SECTION_ALL.equals(section))) {
            currentSection = SECTION_ACTIVE;
        } else {
            currentSection = section;
        }

        int activeBg = getResources().getColor(R.color.primary_burgundy);
        int inactiveBg = getResources().getColor(R.color.action_tile_blue_bg);
        int activeText = Color.WHITE;
        int inactiveText = getResources().getColor(R.color.text_primary);

        if (tabActiveOrders != null) {
            tabActiveOrders.setBackgroundColor(SECTION_ACTIVE.equals(currentSection) ? activeBg : inactiveBg);
            tabActiveOrders.setTextColor(SECTION_ACTIVE.equals(currentSection) ? activeText : inactiveText);
        }

        if (tabReadyOrders != null) {
            tabReadyOrders.setBackgroundColor(SECTION_READY.equals(currentSection) ? activeBg : inactiveBg);
            tabReadyOrders.setTextColor(SECTION_READY.equals(currentSection) ? activeText : inactiveText);
        }

        if (tabCompletedOrders != null) {
            tabCompletedOrders.setBackgroundColor(SECTION_COMPLETED.equals(currentSection) ? activeBg : inactiveBg);
            tabCompletedOrders.setTextColor(SECTION_COMPLETED.equals(currentSection) ? activeText : inactiveText);
        }

        if (tabAllOrders != null) {
            tabAllOrders.setBackgroundColor(SECTION_ALL.equals(currentSection) ? activeBg : inactiveBg);
            tabAllOrders.setTextColor(SECTION_ALL.equals(currentSection) ? activeText : inactiveText);
        }

        filterAndRenderOrders();
    }

    private void setupFilterSpinner() {
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, FILTER_OPTIONS);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilterSpinner.setAdapter(filterAdapter);

        statusFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filterAndRenderOrders(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        adminSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAndRenderOrders(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void initDefaultPriceCatalog() {
        priceCatalog.put("cloth_wash_iron", new LaundryPriceItem("cloth_wash_iron", "Cloth (Wash + Iron)", 15.0, true));
        priceCatalog.put("cloth_iron", new LaundryPriceItem("cloth_iron", "Cloth (Only Ironing)", 7.0, true));
        priceCatalog.put("cloth_wash", new LaundryPriceItem("cloth_wash", "Cloth (Only Washing)", 8.0, true));
        priceCatalog.put("bedsheet", new LaundryPriceItem("bedsheet", "Bed Sheet (Single)", 25.0, true));
        priceCatalog.put("dry_clean", new LaundryPriceItem("dry_clean", "Dry Cleaning", 50.0, true));
        priceCatalog.put("blanket", new LaundryPriceItem("blanket", "Blanket (Easy Wash)", 50.0, true));
        priceCatalog.put("jacket", new LaundryPriceItem("jacket", "Jacket", 100.0, true));
    }

    private void loadPricingConfig() {
        // Load configurable pricing from laundryConfig/pricing per Section 3.3
        pricingListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_LAUNDRY_CONFIG)
            .document(FirestoreConfig.DOC_LAUNDRY_PRICING)
            .addSnapshotListener((snapshot, e) -> {
                if (snapshot != null && snapshot.exists() && snapshot.getData() != null) {
                    Map<String, Object> data = snapshot.getData();
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        if (entry.getValue() instanceof Number) {
                            double price = ((Number) entry.getValue()).doubleValue();
                            if (priceCatalog.containsKey(entry.getKey())) {
                                priceCatalog.get(entry.getKey()).setUnitPrice(price);
                            }
                        }
                    }
                }
            });
    }

    private void listenForOrders() {
        adminOrdersProgressBar.setVisibility(View.VISIBLE);
        adminErrorContainer.setVisibility(View.GONE);
        adminOrdersEmptyText.setVisibility(View.GONE);

        if (ordersListener != null) ordersListener.remove();

        ordersListener = FirestoreConfig.getFirestore()
                .collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS)
                .addSnapshotListener((snapshots, e) -> {
                    adminOrdersProgressBar.setVisibility(View.GONE);

                    if (e != null) {
                        adminErrorContainer.setVisibility(View.VISIBLE);
                        adminErrorText.setText("Firestore Query Error: " + e.getMessage());
                        return;
                    }

                    allOrders.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            allOrders.add(LaundryOrder.fromSnapshot(doc.getId(), doc.getData()));
                        }
                    }

                    // Sort newest first
                    allOrders.sort((o1, o2) -> {
                        if (o1.getCreatedAt() == null || o2.getCreatedAt() == null) return 0;
                        return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                    });

                    updateSummaryStatistics();
                    filterAndRenderOrders();
                });
    }

    private void updateSummaryStatistics() {
        int total = allOrders.size();
        int active = 0;
        int ready = 0;
        int completed = 0;

        for (LaundryOrder ord : allOrders) {
            if (ord == null) continue;
            String s = ord.getStatus() != null ? ord.getStatus().toLowerCase(Locale.ROOT) : "";
            if (s.contains("ready")) {
                ready++;
            } else if (s.contains("collected") || s.contains("completed") || s.contains("cancelled")) {
                completed++;
            } else {
                active++;
            }
        }

        if (statTotalOrders != null) statTotalOrders.setText(String.valueOf(total));
        if (statPendingOrders != null) statPendingOrders.setText(String.valueOf(active));
        if (statProgressOrders != null) statProgressOrders.setText(String.valueOf(ready));
        if (statCompletedOrders != null) statCompletedOrders.setText(String.valueOf(completed));

        if (tabActiveOrders != null) tabActiveOrders.setText("Active Orders (" + active + ")");
        if (tabReadyOrders != null) tabReadyOrders.setText("Ready for Collection (" + ready + ")");
        if (tabCompletedOrders != null) tabCompletedOrders.setText("Completed (" + completed + ")");
    }

    /**
     * Resolves a filter option safely without throwing IndexOutOfBoundsException.
     * When position is invalid (-1 or out of range), safely defaults to "All Statuses".
     */
    public static String resolveFilterOption(int position, String[] filterOptions) {
        if (filterOptions == null || filterOptions.length == 0) {
            return "All Statuses";
        }
        if (position < 0 || position >= filterOptions.length) {
            return filterOptions[0]; // "All Statuses"
        }
        return filterOptions[position];
    }

    /**
     * Resolves a selected status safely without throwing IndexOutOfBoundsException.
     * When position is invalid (-1 or out of range), safely returns fallbackStatus.
     */
    public static String resolveSelectedStatus(int position, String[] supportedStatuses, String fallbackStatus) {
        if (supportedStatuses == null || supportedStatuses.length == 0) {
            return fallbackStatus != null ? fallbackStatus : "";
        }
        if (position < 0 || position >= supportedStatuses.length) {
            return fallbackStatus != null ? fallbackStatus : supportedStatuses[0];
        }
        return supportedStatuses[position];
    }

    /**
     * Calculates the next logical status index in the lifecycle.
     * Safely returns 0 if currentStatus is null or unmatched.
     */
    public static int resolveNextStatusIndex(String currentStatus, String[] supportedStatuses) {
        if (currentStatus == null || supportedStatuses == null || supportedStatuses.length == 0) {
            return 0;
        }
        for (int i = 0; i < supportedStatuses.length; i++) {
            if (supportedStatuses[i] != null && supportedStatuses[i].equalsIgnoreCase(currentStatus.trim())) {
                return Math.min(i + 1, supportedStatuses.length - 1);
            }
        }
        return 0;
    }

    /**
     * Evaluates whether an order satisfies the active section, spinner filter, and search query.
     * Safely handles null orders, unknown statuses, empty fields, and null queries.
     */
    public static boolean matchesFilter(LaundryOrder ord, String currentSection, String selectedFilter, String query) {
        if (ord == null) {
            return false;
        }

        String status = ord.getStatus() != null ? ord.getStatus() : "";
        String sLower = status.toLowerCase(Locale.ROOT);

        // Tab section filtering (Section 2.2)
        String section = currentSection != null ? currentSection : SECTION_ACTIVE;
        if (SECTION_ACTIVE.equals(section)) {
            if (sLower.contains("ready") || sLower.contains("collected") || sLower.contains("completed") || sLower.contains("cancelled")) {
                return false;
            }
        } else if (SECTION_READY.equals(section)) {
            if (!sLower.contains("ready")) {
                return false;
            }
        } else if (SECTION_COMPLETED.equals(section)) {
            if (!sLower.contains("collected") && !sLower.contains("completed") && !sLower.contains("cancelled")) {
                return false;
            }
        } else if (SECTION_ALL.equals(section)) {
            // Keep all
        } else {
            // Unknown or unmatched section: safely fallback to active orders
            if (sLower.contains("ready") || sLower.contains("collected") || sLower.contains("completed") || sLower.contains("cancelled")) {
                return false;
            }
        }

        // Spinner status filter
        if (selectedFilter != null && !selectedFilter.isEmpty() && !"All Statuses".equalsIgnoreCase(selectedFilter)) {
            if (!selectedFilter.equalsIgnoreCase(status)) {
                return false;
            }
        }

        // Search query (Order ID, Student Name, SAP ID, Room, Email)
        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            String id = ord.getOrderId() != null ? ord.getOrderId().toLowerCase(Locale.ROOT) : "";
            String name = ord.getStudentName() != null ? ord.getStudentName().toLowerCase(Locale.ROOT) : "";
            String sap = ord.getSapId() != null ? ord.getSapId().toLowerCase(Locale.ROOT) : "";
            String email = ord.getUserEmail() != null ? ord.getUserEmail().toLowerCase(Locale.ROOT) : "";
            String room = ord.getUserRoom() != null ? ord.getUserRoom().toLowerCase(Locale.ROOT) : "";

            if (!id.contains(q) && !name.contains(q) && !sap.contains(q) && !email.contains(q) && !room.contains(q)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Filters a list of orders according to section, filter string, and query.
     */
    public static List<LaundryOrder> filterOrders(List<LaundryOrder> orders, String currentSection, String selectedFilter, String query) {
        List<LaundryOrder> filtered = new ArrayList<>();
        if (orders == null || orders.isEmpty()) {
            return filtered;
        }
        for (LaundryOrder ord : orders) {
            if (matchesFilter(ord, currentSection, selectedFilter, query)) {
                filtered.add(ord);
            }
        }
        return filtered;
    }

    private void filterAndRenderOrders() {
        if (adminOrdersList == null) {
            return;
        }

        String query = (adminSearchInput != null && adminSearchInput.getText() != null)
                ? adminSearchInput.getText().toString().trim().toLowerCase(Locale.ROOT)
                : "";

        int selectedPosition = statusFilterSpinner != null ? statusFilterSpinner.getSelectedItemPosition() : -1;
        String selectedFilter = resolveFilterOption(selectedPosition, FILTER_OPTIONS);

        List<LaundryOrder> filtered = filterOrders(allOrders, currentSection, selectedFilter, query);

        String labelText = "Orders (" + filtered.size() + ")";
        if (SECTION_ACTIVE.equals(currentSection)) labelText = "Active Orders (" + filtered.size() + ")";
        else if (SECTION_READY.equals(currentSection)) labelText = "Ready for Collection (" + filtered.size() + ")";
        else if (SECTION_COMPLETED.equals(currentSection)) labelText = "Completed Orders (" + filtered.size() + ")";
        if (adminOrdersCountLabel != null) {
            adminOrdersCountLabel.setText(labelText);
        }

        adminOrdersList.removeAllViews();

        if (filtered.isEmpty()) {
            if (adminOrdersEmptyText != null) {
                adminOrdersEmptyText.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (adminOrdersEmptyText != null) {
            adminOrdersEmptyText.setVisibility(View.GONE);
        }
        for (LaundryOrder ord : filtered) {
            if (ord != null) {
                addAdminOrderCard(ord);
            }
        }
    }

    /**
     * Renders an order card per Section 2.2 specifications:
     * - Order ID
     * - Student name
     * - SAP ID
     * - Order status
     * - Creation date
     * - Final price, if confirmed.
     */
    private void addAdminOrderCard(LaundryOrder order) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        // Header: Order ID + Status Badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView idText = new TextView(this);
        String oid = "Order #" + order.getOrderId();
        idText.setText(oid);
        idText.setTextSize(15);
        idText.setTextColor(Color.parseColor("#7A0019"));
        idText.setTypeface(null, Typeface.BOLD);
        header.addView(idText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView badge = new TextView(this);
        badge.setText(order.getStatus());
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        styleStatusBadge(badge, order.getStatus());
        header.addView(badge);

        card.addView(header);

        // Student Name and SAP ID (Mandatory Section 2.2)
        TextView studentInfo = new TextView(this);
        String sName = (order.getStudentName() != null && !order.getStudentName().isEmpty()) ? order.getStudentName() : "Student";
        String sapStr = (order.getSapId() != null && !order.getSapId().isEmpty()) ? " • SAP ID: " + order.getSapId() : "";
        studentInfo.setText(sName + sapStr);
        studentInfo.setTextColor(Color.parseColor("#252525"));
        studentInfo.setTextSize(14);
        studentInfo.setTypeface(null, Typeface.BOLD);
        studentInfo.setPadding(0, dp(4), 0, 0);
        card.addView(studentInfo);

        // Creation Date (Mandatory Section 2.2)
        TextView dateText = new TextView(this);
        String formattedDate = "Created: Recent";
        if (order.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            formattedDate = "Created: " + sdf.format(order.getCreatedAt());
        }
        dateText.setText(formattedDate);
        dateText.setTextColor(Color.parseColor("#707070"));
        dateText.setTextSize(11);
        dateText.setPadding(0, dp(2), 0, 0);
        card.addView(dateText);

        // Items Breakdown
        TextView items = new TextView(this);
        items.setText("Items: " + order.getItemsSummary());
        items.setTextColor(Color.parseColor("#707070"));
        items.setTextSize(12);
        items.setPadding(0, dp(4), 0, 0);
        card.addView(items);

        // Price Row: Final Price if confirmed, else estimated (Section 2.2)
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        priceRow.setPadding(0, dp(6), 0, 0);

        TextView priceText = new TextView(this);
        if (order.isConfirmed()) {
            priceText.setText(String.format(Locale.getDefault(), "Final Price: ₹%.2f (%d garments)",
                    order.getFinalPrice(), order.getTotalItemCount()));
            priceText.setTextColor(Color.parseColor("#27AE60"));
        } else {
            priceText.setText(String.format(Locale.getDefault(), "Estimated: ₹%.2f (Awaiting final confirmation)",
                    order.getEstimatedPrice()));
            priceText.setTextColor(Color.parseColor("#E67E22"));
        }
        priceText.setTextSize(13);
        priceText.setTypeface(null, Typeface.BOLD);
        priceRow.addView(priceText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        card.addView(priceRow);

        // Action Prompt
        TextView actionPrompt = new TextView(this);
        actionPrompt.setText("Tap to update status lifecycle →");
        actionPrompt.setTextColor(Color.parseColor("#0E6251"));
        actionPrompt.setTextSize(11);
        actionPrompt.setPadding(0, dp(6), 0, 0);
        card.addView(actionPrompt);

        card.setOnClickListener(v -> showAdminOrderActionDialog(order));
        adminOrdersList.addView(card);
    }

    private void styleStatusBadge(TextView badge, String status) {
        if (status == null) status = "";
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("ready")) {
            badge.setBackgroundColor(Color.parseColor("#E8F5E9"));
            badge.setTextColor(Color.parseColor("#2E7D32"));
        } else if (s.contains("wash") || s.contains("iron") || s.contains("dry") || s.contains("progress")) {
            badge.setBackgroundColor(Color.parseColor("#E3F2FD"));
            badge.setTextColor(Color.parseColor("#1565C0"));
        } else if (s.contains("collected") || s.contains("completed")) {
            badge.setBackgroundColor(Color.parseColor("#EEEEEE"));
            badge.setTextColor(Color.parseColor("#616161"));
        } else if (s.contains("cancelled")) {
            badge.setBackgroundColor(Color.parseColor("#FFEBEE"));
            badge.setTextColor(Color.parseColor("#C62828"));
        } else {
            badge.setBackgroundColor(Color.parseColor("#FFF8E7"));
            badge.setTextColor(Color.parseColor("#E67E22"));
        }
    }

    /**
     * Create Laundry Order Workflow (Section 2.3):
     * 1. Admin enters SAP ID.
     * 2. Application looks up registered student.
     * 3. If no student exists, show error.
     * 4. If student exists, display verified profile.
     * 5. Admin adds items and quantities from configured price catalog.
     * 6. Calculate total estimated price & confirm final price.
     * 7. Save official order to Firestore.
     */
    private void openCreateOrderDialog() {
        Dialog dialog = new Dialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_create_order, null);
        dialog.setContentView(view);

        EditText sapInput = view.findViewById(R.id.dialogStudentSapIdInput);
        Button btnLookupSap = view.findViewById(R.id.btnLookupSapId);
        ProgressBar lookupProgress = view.findViewById(R.id.lookupProgressBar);
        TextView lookupError = view.findViewById(R.id.sapLookupErrorText);
        LinearLayout verifiedCard = view.findViewById(R.id.verifiedStudentProfileCard);
        TextView verifiedName = view.findViewById(R.id.verifiedStudentNameText);
        TextView verifiedDetails = view.findViewById(R.id.verifiedStudentDetailsText);

        LinearLayout itemsContainer = view.findViewById(R.id.dialogItemsContainer);
        TextView totalGarmentsText = view.findViewById(R.id.dialogTotalClothesCount);
        TextView estimatedPriceText = view.findViewById(R.id.dialogEstimatedPriceText);
        EditText finalPriceInput = view.findViewById(R.id.dialogFinalPriceInput);

        Spinner statusSpinner = view.findViewById(R.id.dialogInitialStatusSpinner);
        EditText remarksInput = view.findViewById(R.id.dialogOrderRemarksInput);
        Button btnSubmit = view.findViewById(R.id.btnSubmitCreateOrder);
        Button btnCancel = view.findViewById(R.id.btnCancelCreateOrder);

        String[] initialStatuses = {LaundryOrder.STATUS_RECEIVED, LaundryOrder.STATUS_REGISTERED, LaundryOrder.STATUS_WASHING};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, initialStatuses);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);

        // Holder for verified student data
        final String[] verifiedStudentUid = {""};
        final String[] verifiedSapId = {""};
        final String[] verifiedStudentName = {""};
        final String[] verifiedEmail = {""};
        final String[] verifiedRoom = {""};
        final String[] verifiedPhone = {""};

        // Item quantities map
        Map<String, Integer> itemQuantities = new HashMap<>();
        for (String key : priceCatalog.keySet()) {
            itemQuantities.put(key, 0);
        }

        // Recalculate totals
        Runnable recalculate = () -> {
            int totalGarments = 0;
            double totalEst = 0.0;
            for (Map.Entry<String, LaundryPriceItem> entry : priceCatalog.entrySet()) {
                int q = itemQuantities.containsKey(entry.getKey()) ? itemQuantities.get(entry.getKey()) : 0;
                totalGarments += q;
                totalEst += (q * entry.getValue().getUnitPrice());
            }

            totalGarmentsText.setText("Total Garments: " + totalGarments);
            estimatedPriceText.setText(String.format(Locale.getDefault(), "Estimated: ₹%.2f", totalEst));

            // Default final confirmed price to estimated price if not manually changed
            String currentFinal = finalPriceInput.getText().toString().trim();
            if (currentFinal.isEmpty() || currentFinal.equals("0") || currentFinal.equals("0.00")) {
                finalPriceInput.setText(String.format(Locale.getDefault(), "%.2f", totalEst));
            }

            boolean canSubmit = !verifiedStudentUid[0].isEmpty() && totalGarments > 0;
            btnSubmit.setEnabled(canSubmit);
        };

        // Render item rows with [-] Qty [+] steppers
        itemsContainer.removeAllViews();
        for (Map.Entry<String, LaundryPriceItem> entry : priceCatalog.entrySet()) {
            LaundryPriceItem item = entry.getValue();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView lbl = new TextView(this);
            lbl.setText(String.format(Locale.getDefault(), "%s (₹%.0f/ea)", item.getName(), item.getUnitPrice()));
            lbl.setTextColor(Color.parseColor("#252525"));
            lbl.setTextSize(13);
            row.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            Button btnMinus = new Button(this);
            btnMinus.setText("−");
            btnMinus.setTextSize(14);
            btnMinus.setBackgroundColor(Color.parseColor("#E0E0E0"));
            LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(dp(36), dp(36));
            btnMinus.setLayoutParams(btnP);
            row.addView(btnMinus);

            TextView qtyView = new TextView(this);
            qtyView.setText("0");
            qtyView.setTextSize(14);
            qtyView.setTypeface(null, Typeface.BOLD);
            qtyView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams qP = new LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT);
            qtyView.setLayoutParams(qP);
            row.addView(qtyView);

            Button btnPlus = new Button(this);
            btnPlus.setText("+");
            btnPlus.setTextSize(14);
            btnPlus.setBackgroundColor(Color.parseColor("#E0E0E0"));
            btnPlus.setLayoutParams(btnP);
            row.addView(btnPlus);

            btnPlus.setOnClickListener(v -> {
                int q = itemQuantities.get(item.getId());
                q++;
                itemQuantities.put(item.getId(), q);
                qtyView.setText(String.valueOf(q));
                recalculate.run();
            });

            btnMinus.setOnClickListener(v -> {
                int q = itemQuantities.get(item.getId());
                if (q > 0) {
                    q--;
                    itemQuantities.put(item.getId(), q);
                    qtyView.setText(String.valueOf(q));
                    recalculate.run();
                }
            });

            itemsContainer.addView(row);
        }

        // Student Lookup Action (Workflow Step 1-4)
        View.OnClickListener doLookup = v -> {
            String sap = sapInput.getText().toString().trim();
            if (sap.isEmpty()) {
                lookupError.setVisibility(View.VISIBLE);
                lookupError.setText("Please enter a student SAP ID.");
                return;
            }

            lookupProgress.setVisibility(View.VISIBLE);
            lookupError.setVisibility(View.GONE);
            verifiedCard.setVisibility(View.GONE);
            btnSubmit.setEnabled(false);

            // Lookup student profile in Cloud Firestore by sapId (or rollNumber)
            FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                .whereEqualTo("sapId", sap)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // Fallback check on rollNumber field
                        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                            .whereEqualTo("rollNumber", sap)
                            .get()
                            .addOnSuccessListener(fallbackSnap -> {
                                lookupProgress.setVisibility(View.GONE);
                                handleStudentLookupResult(fallbackSnap, sap, verifiedStudentUid, verifiedSapId,
                                        verifiedStudentName, verifiedEmail, verifiedRoom, verifiedPhone,
                                        verifiedCard, verifiedName, verifiedDetails, lookupError, recalculate);
                            })
                            .addOnFailureListener(err -> {
                                lookupProgress.setVisibility(View.GONE);
                                lookupError.setVisibility(View.VISIBLE);
                                lookupError.setText("Lookup failed: " + err.getMessage());
                            });
                    } else {
                        lookupProgress.setVisibility(View.GONE);
                        handleStudentLookupResult(querySnapshot, sap, verifiedStudentUid, verifiedSapId,
                                verifiedStudentName, verifiedEmail, verifiedRoom, verifiedPhone,
                                verifiedCard, verifiedName, verifiedDetails, lookupError, recalculate);
                    }
                })
                .addOnFailureListener(e -> {
                    lookupProgress.setVisibility(View.GONE);
                    lookupError.setVisibility(View.VISIBLE);
                    lookupError.setText("Lookup failed: " + e.getMessage());
                });
        };

        btnLookupSap.setOnClickListener(doLookup);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Create Official Order on submit (Section 2.3)
        btnSubmit.setOnClickListener(v -> {
            if (verifiedStudentUid[0].isEmpty()) {
                Toast.makeText(this, "Validate student SAP ID first.", Toast.LENGTH_SHORT).show();
                return;
            }

            int totalClothes = 0;
            double totalEst = 0.0;
            List<Map<String, Object>> selectedItems = new ArrayList<>();

            for (Map.Entry<String, LaundryPriceItem> entry : priceCatalog.entrySet()) {
                int q = itemQuantities.containsKey(entry.getKey()) ? itemQuantities.get(entry.getKey()) : 0;
                if (q > 0) {
                    totalClothes += q;
                    totalEst += (q * entry.getValue().getUnitPrice());

                    Map<String, Object> it = new HashMap<>();
                    it.put("id", entry.getKey());
                    it.put("name", entry.getValue().getName());
                    it.put("quantity", q);
                    it.put("unitPrice", entry.getValue().getUnitPrice());
                    selectedItems.add(it);
                }
            }

            if (totalClothes <= 0) {
                Toast.makeText(this, "Please enter at least 1 garment item.", Toast.LENGTH_SHORT).show();
                return;
            }

            double finalPrice = totalEst;
            String fStr = finalPriceInput.getText().toString().trim();
            if (!fStr.isEmpty()) {
                try {
                    finalPrice = Double.parseDouble(fStr);
                } catch (NumberFormatException ignored) { }
            }

            int selectedStatusPos = statusSpinner != null ? statusSpinner.getSelectedItemPosition() : -1;
            String initialStatus = resolveSelectedStatus(selectedStatusPos, initialStatuses, LaundryOrder.STATUS_RECEIVED);
            String remarks = remarksInput.getText().toString().trim();

            dialog.dismiss();
            createOrderInFirestore(verifiedStudentUid[0], verifiedSapId[0], verifiedStudentName[0],
                    verifiedEmail[0], verifiedRoom[0], verifiedPhone[0], selectedItems,
                    totalClothes, totalEst, finalPrice, initialStatus, remarks);
        });

        dialog.show();
    }

    private void handleStudentLookupResult(QuerySnapshot querySnapshot, String sap,
                                           String[] verifiedStudentUid, String[] verifiedSapId,
                                           String[] verifiedStudentName, String[] verifiedEmail,
                                           String[] verifiedRoom, String[] verifiedPhone,
                                           LinearLayout verifiedCard, TextView verifiedName,
                                           TextView verifiedDetails, TextView lookupError,
                                           Runnable recalculate) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            verifiedStudentUid[0] = "";
            lookupError.setVisibility(View.VISIBLE);
            lookupError.setText("No registered student found for this SAP ID.");
            verifiedCard.setVisibility(View.GONE);
            recalculate.run();
            return;
        }

        // Section 3.1: Enforce 1-to-1 mapping; reject ambiguous matches
        if (querySnapshot.size() > 1) {
            verifiedStudentUid[0] = "";
            lookupError.setVisibility(View.VISIBLE);
            lookupError.setText("Ambiguous match: Multiple profiles found with this SAP ID. Please contact system admin.");
            verifiedCard.setVisibility(View.GONE);
            recalculate.run();
            return;
        }

        DocumentSnapshot studentDoc = querySnapshot.getDocuments().get(0);
        String uid = studentDoc.getId();
        String name = studentDoc.getString("name");
        if (name == null || name.trim().isEmpty()) name = studentDoc.getString("fullName");
        if (name == null) name = "Student";

        String room = studentDoc.getString("roomNumber");
        if (room == null) room = "Campus Hostel";
        String email = studentDoc.getString("email");
        if (email == null) email = "";
        String phone = studentDoc.getString("phone");
        if (phone == null) phone = "";

        verifiedStudentUid[0] = uid;
        verifiedSapId[0] = sap;
        verifiedStudentName[0] = name;
        verifiedEmail[0] = email;
        verifiedRoom[0] = room;
        verifiedPhone[0] = phone;

        lookupError.setVisibility(View.GONE);
        verifiedCard.setVisibility(View.VISIBLE);
        verifiedName.setText(name);
        verifiedDetails.setText("SAP ID: " + sap + " • Room: " + room + " • " + email);

        recalculate.run();
    }

    private void createOrderInFirestore(String studentUid, String sapId, String studentName,
                                        String email, String room, String phone,
                                        List<Map<String, Object>> items, int totalClothes,
                                        double estimatedPrice, double finalPrice,
                                        String status, String remarks) {

        FirebaseUser adminUser = FirebaseAuth.getInstance().getCurrentUser();
        String adminUid = adminUser != null ? adminUser.getUid() : "admin";
        String adminEmail = adminUser != null ? adminUser.getEmail() : "laundry_admin";

        // Generate official order ID
        String orderId = "HB-" + (1000 + new Random().nextInt(9000));

        LaundryOrder order = new LaundryOrder();
        order.setOrderId(orderId);
        order.setStudentUid(studentUid);
        order.setSapId(sapId);
        order.setStudentName(studentName);
        order.setUserEmail(email);
        order.setUserRoom(room);
        order.setUserPhone(phone);
        order.setItems(items);
        order.setTotalClothesCount(totalClothes);
        order.setEstimatedPrice(estimatedPrice);
        order.setFinalPrice(finalPrice);
        order.setStatus(status);
        order.setRemarks(remarks);
        order.setCreatedBy(adminUid);
        order.setPaymentStatus("Unpaid");

        Map<String, Object> orderMap = order.toMap();
        orderMap.put("createdAt", FieldValue.serverTimestamp());
        orderMap.put("updatedAt", FieldValue.serverTimestamp());

        // Audit log entry for status lifecycle (Section 2.4)
        Map<String, Object> initialAudit = new HashMap<>();
        initialAudit.put("status", status);
        initialAudit.put("updatedAt", new Date());
        initialAudit.put("updatedBy", adminEmail);
        if (!remarks.isEmpty()) initialAudit.put("note", remarks);

        List<Map<String, Object>> history = new ArrayList<>();
        history.add(initialAudit);
        orderMap.put("statusHistory", history);

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS)
            .document(orderId)
            .set(orderMap)
            .addOnSuccessListener(aVoid -> {
                new AlertDialog.Builder(this)
                    .setTitle("Order Created Successfully")
                    .setMessage("Official Order #" + orderId + " has been generated for " +
                            studentName + " (SAP ID: " + sapId + ").\nTotal Garments: " + totalClothes +
                            "\nEstimated: ₹" + (int) estimatedPrice + " • Final: ₹" + (int) finalPrice)
                    .setPositiveButton("OK", null)
                    .show();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to create order: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    /**
     * Order Status Management Action Dialog (Section 2.4)
     */
    private void showAdminOrderActionDialog(LaundryOrder order) {
        Dialog dialog = new Dialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_order_action, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.dialogOrderTitle);
        TextView studentInfo = view.findViewById(R.id.dialogStudentInfo);
        TextView summary = view.findViewById(R.id.dialogItemsSummary);
        Spinner statusSpinner = view.findViewById(R.id.dialogStatusSpinner);
        EditText finalPriceEdit = view.findViewById(R.id.dialogEditFinalPrice);
        EditText noteInput = view.findViewById(R.id.dialogAdminNote);
        Button btnCancel = view.findViewById(R.id.btnCancelOrderAction);
        Button btnSubmit = view.findViewById(R.id.btnSubmitOrderAction);

        title.setText("ORDER #" + order.getOrderId());
        studentInfo.setText("Student: " + order.getStudentName() +
                (order.getSapId() != null && !order.getSapId().isEmpty() ? " • SAP ID: " + order.getSapId() : ""));

        summary.setText("Items: " + order.getItemsSummary() +
                "\nEstimated: ₹" + (int) order.getEstimatedPrice() +
                (order.isConfirmed() ? " • Confirmed Final: ₹" + (int) (double) order.getFinalPrice() : "") +
                "\nCurrent Status: " + order.getStatus());

        finalPriceEdit.setText(String.format(Locale.getDefault(), "%.2f", order.getEffectivePrice()));

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SUPPORTED_STATUSES);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);

        // Pre-select next logical lifecycle status safely
        int nextPos = resolveNextStatusIndex(order.getStatus(), SUPPORTED_STATUSES);
        statusSpinner.setSelection(nextPos);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            int selectedStatusPos = statusSpinner != null ? statusSpinner.getSelectedItemPosition() : -1;
            String newStatus = resolveSelectedStatus(selectedStatusPos, SUPPORTED_STATUSES,
                    order.getStatus() != null ? order.getStatus() : SUPPORTED_STATUSES[0]);
            String note = noteInput.getText().toString().trim();

            double newFinalPrice = order.getEffectivePrice();
            String fStr = finalPriceEdit.getText().toString().trim();
            if (!fStr.isEmpty()) {
                try {
                    newFinalPrice = Double.parseDouble(fStr);
                } catch (NumberFormatException ignored) { }
            }

            dialog.dismiss();
            updateOrderStatusInFirestore(order, newStatus, newFinalPrice, note);
        });

        dialog.show();
    }

    private void updateOrderStatusInFirestore(LaundryOrder order, String newStatus, double finalPrice, String adminNote) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String adminUid = user != null ? user.getUid() : "admin";
        String adminEmail = user != null ? user.getEmail() : adminUid;

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("status", newStatus);
        historyEntry.put("updatedAt", new Date());
        historyEntry.put("updatedBy", adminEmail);
        if (!adminNote.isEmpty()) historyEntry.put("note", adminNote);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("finalPrice", finalPrice);
        updates.put("confirmedTotal", finalPrice);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        updates.put("statusHistory", FieldValue.arrayUnion(historyEntry));
        if (!adminNote.isEmpty()) {
            updates.put("remarks", adminNote);
            updates.put("adminNotes", adminNote);
        }

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS)
                .document(order.getOrderId())
                .update(updates)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Order " + order.getOrderId() + " advanced to " + newStatus, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update order: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void openManageRatesDialog() {
        Dialog dialog = new Dialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_rates, null);
        dialog.setContentView(view);

        EditText editWash = view.findViewById(R.id.editWashRate);
        EditText editIron = view.findViewById(R.id.editIronRate);
        EditText editDry = view.findViewById(R.id.editDryRate);
        EditText editBedsheet = view.findViewById(R.id.editBedsheetRate);

        if (priceCatalog.containsKey("cloth_wash")) editWash.setText(String.valueOf((int) priceCatalog.get("cloth_wash").getUnitPrice()));
        if (priceCatalog.containsKey("cloth_iron")) editIron.setText(String.valueOf((int) priceCatalog.get("cloth_iron").getUnitPrice()));
        if (priceCatalog.containsKey("dry_clean")) editDry.setText(String.valueOf((int) priceCatalog.get("dry_clean").getUnitPrice()));
        if (priceCatalog.containsKey("bedsheet")) editBedsheet.setText(String.valueOf((int) priceCatalog.get("bedsheet").getUnitPrice()));

        view.findViewById(R.id.btnCancelRateEdit).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnSaveRates).setOnClickListener(v -> {
            try {
                double newWash = Double.parseDouble(editWash.getText().toString().trim());
                double newIron = Double.parseDouble(editIron.getText().toString().trim());
                double newDry = Double.parseDouble(editDry.getText().toString().trim());
                double newBedsheet = Double.parseDouble(editBedsheet.getText().toString().trim());

                dialog.dismiss();

                Map<String, Object> rateMap = new HashMap<>();
                rateMap.put("cloth_wash", newWash);
                rateMap.put("cloth_iron", newIron);
                rateMap.put("dry_clean", newDry);
                rateMap.put("bedsheet", newBedsheet);
                rateMap.put("wash", newWash);
                rateMap.put("ironing", newIron);
                rateMap.put("dryCleaning", newDry);
                rateMap.put("updatedAt", FieldValue.serverTimestamp());

                // Save to laundryConfig/pricing (Section 3.3) and legacy laundryRates/default
                FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_CONFIG)
                    .document(FirestoreConfig.DOC_LAUNDRY_PRICING)
                    .set(rateMap, SetOptions.merge());

                FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_RATES)
                    .document(FirestoreConfig.DOC_LAUNDRY_DEFAULT_RATES)
                    .set(rateMap, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        if (priceCatalog.containsKey("cloth_wash")) priceCatalog.get("cloth_wash").setUnitPrice(newWash);
                        if (priceCatalog.containsKey("cloth_iron")) priceCatalog.get("cloth_iron").setUnitPrice(newIron);
                        if (priceCatalog.containsKey("dry_clean")) priceCatalog.get("dry_clean").setUnitPrice(newDry);
                        if (priceCatalog.containsKey("bedsheet")) priceCatalog.get("bedsheet").setUnitPrice(newBedsheet);
                        Toast.makeText(this, "Price list updated successfully!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update price list: " + e.getMessage(), Toast.LENGTH_LONG).show());

            } catch (NumberFormatException ex) {
                Toast.makeText(this, "Please enter valid numeric rates.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void goToLogin() {
        LogoutHelper.performSignOut(this);
    }

    private void openScanOrManualDialog() {
        CharSequence[] options = {"📷 Open Camera Scanner", "⌨️ Enter Request ID / Code Manually"};
        new AlertDialog.Builder(this)
            .setTitle("Scan Student Laundry QR")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    startQrScanner();
                } else {
                    openManualTokenInputDialog();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Align Student Laundry QR Code within the viewfinder");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        qrScannerLauncher.launch(options);
    }

    private void openManualTokenInputDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. REQ-1234567");
        input.setSingleLine(true);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(20);
        params.rightMargin = dp(20);
        params.topMargin = dp(10);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("Enter Request ID or QR Token")
            .setView(container)
            .setPositiveButton("Search", (d, which) -> {
                String token = input.getText().toString().trim();
                if (!token.isEmpty()) {
                    lookupAndVerifyRequest(token);
                } else {
                    Toast.makeText(this, "Please enter a valid request ID.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void lookupAndVerifyRequest(String tokenOrId) {
        final Dialog pDialog = new Dialog(this);
        LinearLayout pLayout = new LinearLayout(this);
        pLayout.setOrientation(LinearLayout.HORIZONTAL);
        pLayout.setPadding(dp(20), dp(20), dp(20), dp(20));
        pLayout.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar pBar = new ProgressBar(this);
        TextView pText = new TextView(this);
        pText.setText("  Resolving Laundry Request...");
        pText.setTextColor(Color.parseColor("#252525"));
        pLayout.addView(pBar);
        pLayout.addView(pText);
        pDialog.setContentView(pLayout);
        pDialog.setCancelable(false);
        pDialog.show();

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS)
            .document(tokenOrId)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists() && doc.getData() != null) {
                    pDialog.dismiss();
                    handleResolvedRequest(LaundryRequest.fromSnapshot(doc.getId(), doc.getData()));
                } else {
                    FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS)
                        .whereEqualTo("qrToken", tokenOrId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            pDialog.dismiss();
                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                DocumentSnapshot d = querySnapshot.getDocuments().get(0);
                                handleResolvedRequest(LaundryRequest.fromSnapshot(d.getId(), d.getData()));
                            } else {
                                new AlertDialog.Builder(this)
                                    .setTitle("Request Not Found")
                                    .setMessage("No pending laundry request found matching:\n" + tokenOrId)
                                    .setPositiveButton("OK", null)
                                    .show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            pDialog.dismiss();
                            Toast.makeText(this, "Lookup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                }
            })
            .addOnFailureListener(e -> {
                pDialog.dismiss();
                Toast.makeText(this, "Lookup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void handleResolvedRequest(LaundryRequest request) {
        String status = request.getStatus();
        if (LaundryRequest.STATUS_CONFIRMED.equalsIgnoreCase(status)) {
            new AlertDialog.Builder(this)
                .setTitle("Request Already Confirmed")
                .setMessage("Request #" + request.getRequestId() + " was ALREADY confirmed into Order #" +
                        request.getConfirmedOrderId() +
                        (request.getConfirmedBy() != null && !request.getConfirmedBy().isEmpty() ? " by " + request.getConfirmedBy() : "") +
                        ".\n\nDuplicate confirmation is prevented.")
                .setPositiveButton("View Active Orders", (d, w) -> selectSection(SECTION_ACTIVE))
                .setNegativeButton("Close", null)
                .show();
            return;
        }

        if (LaundryRequest.STATUS_CANCELLED.equalsIgnoreCase(status)) {
            new AlertDialog.Builder(this)
                .setTitle("Request Cancelled")
                .setMessage("Request #" + request.getRequestId() + " was cancelled by the student.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        if (LaundryRequest.STATUS_REJECTED.equalsIgnoreCase(status)) {
            new AlertDialog.Builder(this)
                .setTitle("Request Rejected")
                .setMessage("Request #" + request.getRequestId() + " was previously rejected.\nReason: " +
                        (request.getRejectionReason() != null ? request.getRejectionReason() : "None provided."))
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        showAdminVerifyAndConfirmDialog(request);
    }

    private void showAdminVerifyAndConfirmDialog(LaundryRequest request) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundResource(R.drawable.bg_card);

        // Header
        TextView title = new TextView(this);
        title.setText("VERIFY & CONFIRM LAUNDRY REQUEST");
        title.setTextColor(Color.parseColor("#7A0019"));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Physically count garments, adjust quantities & prices before confirmation");
        sub.setTextColor(Color.parseColor("#707070"));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        // Student Card
        LinearLayout studentCard = new LinearLayout(this);
        studentCard.setOrientation(LinearLayout.VERTICAL);
        studentCard.setBackgroundColor(Color.parseColor("#F9F9F9"));
        studentCard.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView tvStudent = new TextView(this);
        tvStudent.setText("Student: " + request.getStudentName() +
                (!request.getSapId().isEmpty() ? " • SAP ID: " + request.getSapId() : ""));
        tvStudent.setTextColor(Color.parseColor("#252525"));
        tvStudent.setTextSize(13);
        tvStudent.setTypeface(null, Typeface.BOLD);
        studentCard.addView(tvStudent);

        TextView tvMeta = new TextView(this);
        tvMeta.setText("Request #" + request.getRequestId() +
                (!request.getUserRoom().isEmpty() ? " • Room: " + request.getUserRoom() : "") +
                (!request.getUserEmail().isEmpty() ? " • " + request.getUserEmail() : ""));
        tvMeta.setTextColor(Color.parseColor("#707070"));
        tvMeta.setTextSize(11);
        studentCard.addView(tvMeta);

        root.addView(studentCard);

        // Items working list
        final List<Map<String, Object>> workingItems = new ArrayList<>();
        if (request.getItems() != null) {
            for (Map<String, Object> it : request.getItems()) {
                Map<String, Object> copy = new HashMap<>(it);
                workingItems.add(copy);
            }
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.topMargin = dp(8);
        scrollParams.bottomMargin = dp(8);
        scroll.setLayoutParams(scrollParams);

        LinearLayout itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemsContainer);
        root.addView(scroll);

        // Recalculation targets
        TextView tvTotalCount = new TextView(this);
        TextView tvEstCost = new TextView(this);
        EditText finalPriceInput = new EditText(this);
        finalPriceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final Runnable recalculate = () -> {
            int totalQty = 0;
            double totalCost = 0.0;
            for (Map<String, Object> it : workingItems) {
                int q = 0;
                Object qObj = it.get("quantity");
                if (qObj instanceof Number) q = ((Number) qObj).intValue();

                double p = 0.0;
                Object pObj = it.get("unitPrice");
                if (pObj instanceof Number) p = ((Number) pObj).doubleValue();

                totalQty += q;
                totalCost += (q * p);
            }
            tvTotalCount.setText("Verified Garments: " + totalQty);
            tvEstCost.setText(String.format(Locale.getDefault(), "Calculated: ₹%.0f", totalCost));
            finalPriceInput.setText(String.format(Locale.getDefault(), "%.0f", totalCost));
        };

        // Populate items list
        final Runnable populateList = new Runnable() {
            @Override
            public void run() {
                itemsContainer.removeAllViews();
                for (int i = 0; i < workingItems.size(); i++) {
                    final int index = i;
                    Map<String, Object> it = workingItems.get(index);
                    String name = (String) it.get("name");
                    if (name == null) name = "Item";

                    int qty = 0;
                    Object qObj = it.get("quantity");
                    if (qObj instanceof Number) qty = ((Number) qObj).intValue();

                    double rate = 0.0;
                    Object rObj = it.get("unitPrice");
                    if (rObj instanceof Number) rate = ((Number) rObj).doubleValue();

                    LinearLayout row = new LinearLayout(LaundryAdminActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(4), 0, dp(4));

                    LinearLayout nameBox = new LinearLayout(LaundryAdminActivity.this);
                    nameBox.setOrientation(LinearLayout.VERTICAL);
                    nameBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView tvName = new TextView(LaundryAdminActivity.this);
                    tvName.setText(name);
                    tvName.setTextColor(Color.parseColor("#252525"));
                    tvName.setTextSize(13);
                    tvName.setTypeface(null, Typeface.BOLD);
                    nameBox.addView(tvName);

                    TextView tvRate = new TextView(LaundryAdminActivity.this);
                    tvRate.setText(String.format(Locale.getDefault(), "₹%.0f / each", rate));
                    tvRate.setTextColor(Color.parseColor("#707070"));
                    tvRate.setTextSize(11);
                    nameBox.addView(tvRate);

                    row.addView(nameBox);

                    // Stepper: Minus
                    Button btnMinus = new Button(LaundryAdminActivity.this);
                    btnMinus.setText("−");
                    btnMinus.setTextSize(14);
                    btnMinus.setTextColor(Color.parseColor("#7A0019"));
                    btnMinus.setBackgroundColor(Color.parseColor("#F5F5F5"));
                    LinearLayout.LayoutParams btnMinusParams = new LinearLayout.LayoutParams(dp(36), dp(36));
                    btnMinus.setLayoutParams(btnMinusParams);
                    row.addView(btnMinus);

                    TextView tvQty = new TextView(LaundryAdminActivity.this);
                    tvQty.setText(String.valueOf(qty));
                    tvQty.setTextColor(Color.parseColor("#252525"));
                    tvQty.setTextSize(13);
                    tvQty.setTypeface(null, Typeface.BOLD);
                    tvQty.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams qtyParams = new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT);
                    tvQty.setLayoutParams(qtyParams);
                    row.addView(tvQty);

                    // Stepper: Plus
                    Button btnPlus = new Button(LaundryAdminActivity.this);
                    btnPlus.setText("+");
                    btnPlus.setTextSize(14);
                    btnPlus.setTextColor(Color.parseColor("#7A0019"));
                    btnPlus.setBackgroundColor(Color.parseColor("#F5F5F5"));
                    LinearLayout.LayoutParams btnPlusParams = new LinearLayout.LayoutParams(dp(36), dp(36));
                    btnPlus.setLayoutParams(btnPlusParams);
                    row.addView(btnPlus);

                    final int currentQ = qty;
                    btnPlus.setOnClickListener(v -> {
                        it.put("quantity", currentQ + 1);
                        tvQty.setText(String.valueOf(currentQ + 1));
                        recalculate.run();
                    });

                    btnMinus.setOnClickListener(v -> {
                        if (currentQ > 0) {
                            it.put("quantity", currentQ - 1);
                            tvQty.setText(String.valueOf(currentQ - 1));
                            recalculate.run();
                        }
                    });

                    itemsContainer.addView(row);
                }
            }
        };

        populateList.run();

        // Add Extra Item Button
        Button btnAddExtra = new Button(this);
        btnAddExtra.setText("+ Add Other Item from Catalog");
        btnAddExtra.setTextSize(12);
        btnAddExtra.setTextColor(Color.parseColor("#1565C0"));
        btnAddExtra.setBackgroundColor(Color.TRANSPARENT);
        btnAddExtra.setOnClickListener(v -> {
            List<String> catalogNames = new ArrayList<>();
            List<LaundryPriceItem> catalogList = new ArrayList<>(priceCatalog.values());
            for (LaundryPriceItem p : catalogList) {
                catalogNames.add(p.getName() + " (₹" + (int) p.getUnitPrice() + ")");
            }
            CharSequence[] names = catalogNames.toArray(new CharSequence[0]);
            new AlertDialog.Builder(this)
                .setTitle("Select Clothing Item to Add")
                .setItems(names, (d, which) -> {
                    LaundryPriceItem selected = catalogList.get(which);
                    Map<String, Object> newItem = new HashMap<>();
                    newItem.put("id", selected.getId());
                    newItem.put("name", selected.getName());
                    newItem.put("quantity", 1);
                    newItem.put("unitPrice", selected.getUnitPrice());
                    workingItems.add(newItem);
                    populateList.run();
                    recalculate.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        root.addView(btnAddExtra);

        // Totals & Overrides Box
        LinearLayout totalsBox = new LinearLayout(this);
        totalsBox.setOrientation(LinearLayout.VERTICAL);
        totalsBox.setBackgroundColor(Color.parseColor("#FFF8E7"));
        totalsBox.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout countsRow = new LinearLayout(this);
        countsRow.setOrientation(LinearLayout.HORIZONTAL);

        tvTotalCount.setTextColor(Color.parseColor("#252525"));
        tvTotalCount.setTextSize(13);
        tvTotalCount.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTotalCount.setLayoutParams(countParams);
        countsRow.addView(tvTotalCount);

        tvEstCost.setTextColor(Color.parseColor("#7A0019"));
        tvEstCost.setTextSize(13);
        tvEstCost.setTypeface(null, Typeface.BOLD);
        countsRow.addView(tvEstCost);
        totalsBox.addView(countsRow);

        // Final Price Override
        LinearLayout priceOverrideRow = new LinearLayout(this);
        priceOverrideRow.setOrientation(LinearLayout.HORIZONTAL);
        priceOverrideRow.setGravity(Gravity.CENTER_VERTICAL);
        priceOverrideRow.setPadding(0, dp(4), 0, 0);

        TextView tvFinalLabel = new TextView(this);
        tvFinalLabel.setText("Final Confirmed Price (₹): ");
        tvFinalLabel.setTextColor(Color.parseColor("#252525"));
        tvFinalLabel.setTextSize(13);
        priceOverrideRow.addView(tvFinalLabel);

        LinearLayout.LayoutParams fpParams = new LinearLayout.LayoutParams(dp(100), dp(36));
        finalPriceInput.setLayoutParams(fpParams);
        finalPriceInput.setTextSize(13);
        finalPriceInput.setPadding(dp(6), 0, dp(6), 0);
        priceOverrideRow.addView(finalPriceInput);

        totalsBox.addView(priceOverrideRow);

        root.addView(totalsBox);

        // Remarks Input
        EditText remarksInput = new EditText(this);
        remarksInput.setHint("Admin remarks / instructions (optional)");
        remarksInput.setTextSize(12);
        remarksInput.setPadding(dp(4), dp(6), dp(4), dp(6));
        root.addView(remarksInput);

        // Run initial calculation
        recalculate.run();

        // Action Buttons Row: Reject | Confirm
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(12), 0, 0);

        Button btnReject = new Button(this);
        btnReject.setText("Reject");
        btnReject.setBackgroundColor(Color.parseColor("#FFEBEE"));
        btnReject.setTextColor(Color.parseColor("#C62828"));
        btnReject.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
        rejectParams.rightMargin = dp(6);
        rejectParams.setMarginEnd(dp(6));
        btnReject.setLayoutParams(rejectParams);

        btnReject.setOnClickListener(v -> {
            final EditText reasonInput = new EditText(this);
            reasonInput.setHint("e.g. Items count did not match, unserviceable cloth");
            new AlertDialog.Builder(this)
                .setTitle("Reject Laundry Request")
                .setMessage("Enter reason for rejection:")
                .setView(reasonInput)
                .setPositiveButton("Reject Request", (rd, rw) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.isEmpty()) reason = "Items physically unverified or count mismatched.";
                    dialog.dismiss();

                    final String finalReason = reason;
                    FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS)
                        .document(request.getRequestId())
                        .update("status", LaundryRequest.STATUS_REJECTED,
                                "rejectionReason", finalReason,
                                "updatedAt", FieldValue.serverTimestamp())
                        .addOnSuccessListener(aVoid -> {
                            FcmHelper.recordNotification(
                                request.getStudentUid(),
                                "Laundry Request Rejected",
                                "Your request #" + request.getRequestId() + " was rejected at the counter.\nReason: " + finalReason,
                                "laundry",
                                request.getRequestId()
                            );
                            Toast.makeText(this, "Request #" + request.getRequestId() + " rejected.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to reject: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        actionRow.addView(btnReject);

        Button btnConfirm = new Button(this);
        btnConfirm.setText("Confirm Order");
        btnConfirm.setBackgroundColor(Color.parseColor("#7A0019"));
        btnConfirm.setTextColor(Color.WHITE);
        btnConfirm.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(44), 1.5f);
        btnConfirm.setLayoutParams(confirmParams);

        btnConfirm.setOnClickListener(v -> {
            int verifiedTotalClothes = 0;
            double calculatedEst = 0.0;
            List<Map<String, Object>> verifiedList = new ArrayList<>();

            for (Map<String, Object> it : workingItems) {
                int q = 0;
                Object qObj = it.get("quantity");
                if (qObj instanceof Number) q = ((Number) qObj).intValue();

                double p = 0.0;
                Object pObj = it.get("unitPrice");
                if (pObj instanceof Number) p = ((Number) pObj).doubleValue();

                if (q > 0) {
                    verifiedTotalClothes += q;
                    calculatedEst += (q * p);
                    Map<String, Object> verifiedItem = new HashMap<>(it);
                    verifiedItem.put("quantity", q);
                    verifiedItem.put("unitPrice", p);
                    verifiedItem.put("subtotal", q * p);
                    verifiedList.add(verifiedItem);
                }
            }

            if (verifiedTotalClothes <= 0) {
                Toast.makeText(this, "Verified garments count must be at least 1.", Toast.LENGTH_SHORT).show();
                return;
            }

            double confirmedFinalPrice = calculatedEst;
            String fpStr = finalPriceInput.getText().toString().trim();
            if (!fpStr.isEmpty()) {
                try {
                    confirmedFinalPrice = Double.parseDouble(fpStr);
                } catch (NumberFormatException ignored) { }
            }

            String adminRemarks = remarksInput.getText().toString().trim();
            btnConfirm.setEnabled(false);
            btnConfirm.setText("Confirming...");

            executeOrderConfirmationTransaction(dialog, request, verifiedList, verifiedTotalClothes,
                    calculatedEst, confirmedFinalPrice, adminRemarks);
        });
        actionRow.addView(btnConfirm);

        root.addView(actionRow);

        dialog.setContentView(root);
        dialog.show();
    }

    private void executeOrderConfirmationTransaction(Dialog dialog, LaundryRequest request,
                                                    List<Map<String, Object>> verifiedItems,
                                                    int totalClothes, double estPrice,
                                                    double finalPrice, String remarks) {

        FirebaseUser adminUser = FirebaseAuth.getInstance().getCurrentUser();
        String adminUid = adminUser != null ? adminUser.getUid() : "admin";
        String adminEmail = adminUser != null ? adminUser.getEmail() : "laundry_admin";

        FirebaseFirestore db = FirestoreConfig.getFirestore();
        DocumentReference reqRef = db.collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS).document(request.getRequestId());

        String newOrderId = "HB-" + (1000 + new Random().nextInt(9000));
        DocumentReference orderRef = db.collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS).document(newOrderId);

        db.runTransaction(transaction -> {
            DocumentSnapshot freshReqSnap = transaction.get(reqRef);
            if (!freshReqSnap.exists()) {
                throw new FirebaseFirestoreException("Laundry request #" + request.getRequestId() + " not found.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            String currentStatus = freshReqSnap.getString("status");
            if (!LaundryRequest.STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(currentStatus)) {
                throw new FirebaseFirestoreException("Request is no longer awaiting confirmation (Status: " + currentStatus + ").",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // Build official confirmed order
            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("orderId", newOrderId);
            orderMap.put("requestId", request.getRequestId());
            orderMap.put("studentUid", request.getStudentUid());
            orderMap.put("userId", request.getStudentUid());
            orderMap.put("sapId", request.getSapId());
            orderMap.put("studentName", request.getStudentName());
            orderMap.put("userEmail", request.getUserEmail());
            orderMap.put("userRoom", request.getUserRoom());
            orderMap.put("userPhone", request.getUserPhone());
            orderMap.put("items", verifiedItems);
            orderMap.put("totalClothesCount", totalClothes);
            orderMap.put("estimatedPrice", estPrice);
            orderMap.put("estimatedTotal", estPrice);
            orderMap.put("finalPrice", finalPrice);
            orderMap.put("confirmedTotal", finalPrice);
            orderMap.put("status", LaundryOrder.STATUS_RECEIVED);
            orderMap.put("isConfirmed", true);
            orderMap.put("remarks", remarks);
            orderMap.put("createdBy", adminUid);
            orderMap.put("paymentStatus", "Unpaid");
            orderMap.put("createdAt", FieldValue.serverTimestamp());
            orderMap.put("updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> audit = new HashMap<>();
            audit.put("status", LaundryOrder.STATUS_RECEIVED);
            audit.put("updatedAt", new Date());
            audit.put("updatedBy", adminEmail);
            audit.put("note", "Confirmed via QR desk scan. " + remarks);
            orderMap.put("statusHistory", Collections.singletonList(audit));

            // 1. Create order in laundryOrders
            transaction.set(orderRef, orderMap);

            // 2. Mark request as confirmed in laundryRequests
            Map<String, Object> reqUpdates = new HashMap<>();
            reqUpdates.put("status", LaundryRequest.STATUS_CONFIRMED);
            reqUpdates.put("confirmedOrderId", newOrderId);
            reqUpdates.put("confirmedBy", adminEmail);
            reqUpdates.put("confirmedAt", new Date());
            reqUpdates.put("updatedAt", FieldValue.serverTimestamp());
            reqUpdates.put("finalPrice", finalPrice);
            reqUpdates.put("verifiedClothesCount", totalClothes);
            transaction.update(reqRef, reqUpdates);

            return newOrderId;
        }).addOnSuccessListener(orderId -> {
            dialog.dismiss();

            // Record notification for student
            FcmHelper.recordNotification(
                request.getStudentUid(),
                "Laundry Order Confirmed!",
                "Your laundry request #" + request.getRequestId() + " was confirmed as Order #" + orderId +
                        " (" + totalClothes + " garments, ₹" + (int) finalPrice + ").",
                "laundry",
                orderId
            );

            new AlertDialog.Builder(this)
                .setTitle("Order Confirmed Successfully")
                .setMessage("Official Order #" + orderId + " has been generated for " +
                        request.getStudentName() + " (SAP: " + request.getSapId() + ").\n\n" +
                        "Total Garments: " + totalClothes + "\nFinal Price: ₹" + (int) finalPrice)
                .setPositiveButton("OK", (d, w) -> selectSection(SECTION_ACTIVE))
                .show();
        }).addOnFailureListener(e -> {
            new AlertDialog.Builder(this)
                .setTitle("Confirmation Failed")
                .setMessage("Could not confirm order: " + e.getMessage())
                .setPositiveButton("OK", null)
                .show();
        });
    }

    @Override
    protected void onDestroy() {
        if (ordersListener != null) ordersListener.remove();
        if (pricingListener != null) pricingListener.remove();
        super.onDestroy();
    }
}
