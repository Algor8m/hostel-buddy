package com.example.hostelbuddy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Student Laundry Dashboard & Real-Time Tracking (Section 4).
 * Provides:
 * - Active Orders
 * - Completed Orders
 * - Order History
 * - Price Estimator with live calculation
 * - Laundry Request & QR Generation
 * - Real-time Notifications & Alerts
 */
public class LaundryActivity extends AppCompatActivity {

    private static final String TAB_ACTIVE = "ACTIVE";
    private static final String TAB_COMPLETED = "COMPLETED";
    private static final String TAB_HISTORY = "HISTORY";

    private String currentTab = TAB_ACTIVE;

    private TextView clothesCountNumber, reminderScheduleText, notificationTitleText;
    private TextView ordersCountBadge, ordersEmptyText;
    private TextView calcTotalQuantityText, calcTotalCostText;
    private LinearLayout ordersListLayout, priceItemsContainer;
    private Button btnTabActive, btnTabCompleted, btnTabHistory;

    // Dynamic Pending Request UI
    private LinearLayout pendingRequestContainer;
    private TextView pendingRequestIdTitle, pendingRequestStatusBadge, pendingRequestSummaryText;
    private Button btnViewPendingQr, btnCancelPendingRequest, btnBottomGenerateQr;
    private LaundryRequest activePendingRequest = null;

    private ListenerRegistration pricesListener;
    private ListenerRegistration ordersListener;
    private ListenerRegistration requestsListener;

    private String studentName = "Student";
    private String studentSapId = "";
    private String studentRoom = "";
    private String studentEmail = "";

    private final List<LaundryOrder> activeOrdersList = new ArrayList<>();
    private final List<LaundryOrder> completedOrdersList = new ArrayList<>();
    private final List<LaundryOrder> allOrdersList = new ArrayList<>();

    private final List<LaundryPriceItem> priceCatalog = new ArrayList<>();
    private final Map<String, Integer> itemQuantities = new HashMap<>();
    private final Map<String, TextView> itemQtyViews = new HashMap<>();

    private static final String[][] DEFAULT_ITEMS = {
        {"cloth_wash_iron", "Cloth (Wash + Iron)", "15"},
        {"cloth_iron", "Cloth (Only Ironing)", "7"},
        {"cloth_wash", "Cloth (Only Washing)", "8"},
        {"napkin_softener", "Napkin (Softener Wash)", "8"},
        {"bedsheet", "Bed Sheet (Single)", "25"},
        {"blanket", "Blanket (Easy Wash)", "50"},
        {"pillow_cover", "Pillow Cover", "8"},
        {"dry_clean", "Dry Cleaning", "50"},
        {"jacket", "Jacket", "100"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_laundry);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        clothesCountNumber = findViewById(R.id.clothesCountNumber);
        reminderScheduleText = findViewById(R.id.reminderScheduleText);
        notificationTitleText = findViewById(R.id.notificationTitleText);
        ordersCountBadge = findViewById(R.id.ordersCountBadge);
        ordersEmptyText = findViewById(R.id.ordersEmptyText);
        calcTotalQuantityText = findViewById(R.id.calcTotalQuantityText);
        calcTotalCostText = findViewById(R.id.calcTotalCostText);
        ordersListLayout = findViewById(R.id.ordersListLayout);
        priceItemsContainer = findViewById(R.id.priceItemsContainer);

        btnTabActive = findViewById(R.id.btnTabActive);
        btnTabCompleted = findViewById(R.id.btnTabCompleted);
        btnTabHistory = findViewById(R.id.btnTabHistory);

        btnTabActive.setOnClickListener(v -> selectTab(TAB_ACTIVE));
        btnTabCompleted.setOnClickListener(v -> selectTab(TAB_COMPLETED));
        btnTabHistory.setOnClickListener(v -> selectTab(TAB_HISTORY));

        // Pending Request Views
        pendingRequestContainer = findViewById(R.id.pendingRequestContainer);
        pendingRequestIdTitle = findViewById(R.id.pendingRequestIdTitle);
        pendingRequestStatusBadge = findViewById(R.id.pendingRequestStatusBadge);
        pendingRequestSummaryText = findViewById(R.id.pendingRequestSummaryText);
        btnViewPendingQr = findViewById(R.id.btnViewPendingQr);
        btnCancelPendingRequest = findViewById(R.id.btnCancelPendingRequest);
        btnBottomGenerateQr = findViewById(R.id.btnBottomGenerateQr);

        btnViewPendingQr.setOnClickListener(v -> {
            if (activePendingRequest != null) {
                openQrDisplayDialog(activePendingRequest);
            }
        });

        btnCancelPendingRequest.setOnClickListener(v -> {
            if (activePendingRequest != null) {
                confirmAndCancelPendingRequest(activePendingRequest);
            }
        });

        btnBottomGenerateQr.setOnClickListener(v -> initiateRequestFlow());
        findViewById(R.id.btnGenerateSlip).setOnClickListener(v -> initiateRequestFlow());
        findViewById(R.id.btnResetEstimator).setOnClickListener(v -> resetQuantities());

        // Sync FCM push token for background notifications
        FcmHelper.retrieveAndSyncToken(this);

        loadStudentProfile(currentUser);
        initDefaultCatalog();
        listenToPriceCatalog();
        listenToStudentOrders(currentUser.getUid());
        listenToStudentRequests(currentUser.getUid());
    }

    private void loadStudentProfile(FirebaseUser currentUser) {
        studentEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
        studentName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Student";

        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .document(currentUser.getUid())
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String name = snapshot.getString("name");
                    if (name == null || name.trim().isEmpty()) name = snapshot.getString("fullName");
                    if (name != null) studentName = name;

                    String sap = snapshot.getString("sapId");
                    if (sap == null || sap.trim().isEmpty()) sap = snapshot.getString("rollNumber");
                    if (sap != null) studentSapId = sap;

                    String room = snapshot.getString("roomNumber");
                    if (room != null) studentRoom = room;
                }
            });
    }

    private void selectTab(String tab) {
        currentTab = tab;

        int activeBg = getResources().getColor(R.color.primary_burgundy);
        int inactiveBg = getResources().getColor(R.color.action_tile_blue_bg);
        int activeText = Color.WHITE;
        int inactiveText = getResources().getColor(R.color.text_primary);

        btnTabActive.setBackgroundColor(TAB_ACTIVE.equals(tab) ? activeBg : inactiveBg);
        btnTabActive.setTextColor(TAB_ACTIVE.equals(tab) ? activeText : inactiveText);

        btnTabCompleted.setBackgroundColor(TAB_COMPLETED.equals(tab) ? activeBg : inactiveBg);
        btnTabCompleted.setTextColor(TAB_COMPLETED.equals(tab) ? activeText : inactiveText);

        btnTabHistory.setBackgroundColor(TAB_HISTORY.equals(tab) ? activeBg : inactiveBg);
        btnTabHistory.setTextColor(TAB_HISTORY.equals(tab) ? activeText : inactiveText);

        renderCurrentTabOrders();
    }

    private void renderCurrentTabOrders() {
        ordersListLayout.removeAllViews();

        List<LaundryOrder> displayList;
        String badgeText;
        String emptyMessage;

        if (TAB_ACTIVE.equals(currentTab)) {
            displayList = activeOrdersList;
            badgeText = "Showing Active Orders (" + displayList.size() + ")";
            emptyMessage = "No active laundry orders.\nDrop off clothes at the counter with your SAP ID to begin.";
        } else if (TAB_COMPLETED.equals(currentTab)) {
            displayList = completedOrdersList;
            badgeText = "Showing Completed Orders (" + displayList.size() + ")";
            emptyMessage = "No completed laundry orders yet.";
        } else {
            displayList = allOrdersList;
            badgeText = "All Order History (" + displayList.size() + ")";
            emptyMessage = "No order history found.";
        }

        ordersCountBadge.setText(badgeText);

        if (displayList.isEmpty()) {
            ordersEmptyText.setVisibility(View.VISIBLE);
            ordersEmptyText.setText(emptyMessage);
            ordersListLayout.addView(ordersEmptyText);
            return;
        }

        ordersEmptyText.setVisibility(View.GONE);
        for (LaundryOrder ord : displayList) {
            ordersListLayout.addView(createOrderCardView(ord));
        }
    }

    private void initDefaultCatalog() {
        priceCatalog.clear();
        for (String[] def : DEFAULT_ITEMS) {
            priceCatalog.add(new LaundryPriceItem(def[0], def[1], Double.parseDouble(def[2]), true));
            itemQuantities.put(def[0], 0);
        }
        renderPriceItems();
        recalculateEstimate();
    }

    private void listenToPriceCatalog() {
        // Listen to laundryConfig/pricing per Section 3.3
        pricesListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_LAUNDRY_CONFIG)
            .document(FirestoreConfig.DOC_LAUNDRY_PRICING)
            .addSnapshotListener((snapshot, error) -> {
                if (snapshot != null && snapshot.exists() && snapshot.getData() != null) {
                    Map<String, Object> data = snapshot.getData();
                    for (LaundryPriceItem item : priceCatalog) {
                        if (data.containsKey(item.getId()) && data.get(item.getId()) instanceof Number) {
                            item.setUnitPrice(((Number) data.get(item.getId())).doubleValue());
                        }
                    }
                    renderPriceItems();
                    recalculateEstimate();
                }
            });
    }

    private void renderPriceItems() {
        priceItemsContainer.removeAllViews();
        itemQtyViews.clear();

        for (LaundryPriceItem item : priceCatalog) {
            View row = createPriceRowView(item);
            priceItemsContainer.addView(row);
        }
    }

    private View createPriceRowView(LaundryPriceItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(8), 0, dpToPx(8));

        LinearLayout nameContainer = new LinearLayout(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        nameContainer.setLayoutParams(nameParams);
        nameContainer.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(item.getName());
        tvName.setTextColor(Color.parseColor("#252525"));
        tvName.setTextSize(14);
        tvName.setTypeface(null, Typeface.BOLD);
        nameContainer.addView(tvName);

        TextView tvPrice = new TextView(this);
        tvPrice.setText("₹ " + (int) item.getUnitPrice() + " / each");
        tvPrice.setTextColor(Color.parseColor("#707070"));
        tvPrice.setTextSize(12);
        tvPrice.setPadding(0, dpToPx(2), 0, 0);
        nameContainer.addView(tvPrice);

        row.addView(nameContainer);

        LinearLayout stepper = new LinearLayout(this);
        stepper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnMinus = new TextView(this);
        btnMinus.setText("−");
        btnMinus.setTextSize(18);
        btnMinus.setTextColor(Color.parseColor("#7A0019"));
        btnMinus.setTypeface(null, Typeface.BOLD);
        btnMinus.setGravity(Gravity.CENTER);
        btnMinus.setBackgroundResource(R.drawable.bg_stepper_btn);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        btnMinus.setLayoutParams(btnParams);
        stepper.addView(btnMinus);

        TextView tvQty = new TextView(this);
        int currentQty = itemQuantities.containsKey(item.getId()) ? itemQuantities.get(item.getId()) : 0;
        tvQty.setText(String.valueOf(currentQty));
        tvQty.setTextSize(14);
        tvQty.setTypeface(null, Typeface.BOLD);
        tvQty.setTextColor(Color.parseColor("#252525"));
        tvQty.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qtyParams = new LinearLayout.LayoutParams(dpToPx(36), LinearLayout.LayoutParams.WRAP_CONTENT);
        tvQty.setLayoutParams(qtyParams);
        itemQtyViews.put(item.getId(), tvQty);
        stepper.addView(tvQty);

        TextView btnPlus = new TextView(this);
        btnPlus.setText("+");
        btnPlus.setTextSize(18);
        btnPlus.setTextColor(Color.parseColor("#7A0019"));
        btnPlus.setTypeface(null, Typeface.BOLD);
        btnPlus.setGravity(Gravity.CENTER);
        btnPlus.setBackgroundResource(R.drawable.bg_stepper_btn);
        btnPlus.setLayoutParams(btnParams);
        stepper.addView(btnPlus);

        row.addView(stepper);

        btnPlus.setOnClickListener(v -> {
            int q = itemQuantities.containsKey(item.getId()) ? itemQuantities.get(item.getId()) : 0;
            q++;
            itemQuantities.put(item.getId(), q);
            tvQty.setText(String.valueOf(q));
            recalculateEstimate();
        });

        btnMinus.setOnClickListener(v -> {
            int q = itemQuantities.containsKey(item.getId()) ? itemQuantities.get(item.getId()) : 0;
            if (q > 0) {
                q--;
                itemQuantities.put(item.getId(), q);
                tvQty.setText(String.valueOf(q));
                recalculateEstimate();
            }
        });

        return row;
    }

    private void recalculateEstimate() {
        int totalQty = 0;
        double totalCost = 0.0;

        for (LaundryPriceItem item : priceCatalog) {
            int q = itemQuantities.containsKey(item.getId()) ? itemQuantities.get(item.getId()) : 0;
            totalQty += q;
            totalCost += (q * item.getUnitPrice());
        }

        calcTotalQuantityText.setText(String.valueOf(totalQty));
        calcTotalCostText.setText("₹ " + (int) totalCost);
    }

    private void resetQuantities() {
        for (String id : itemQuantities.keySet()) {
            itemQuantities.put(id, 0);
            TextView tv = itemQtyViews.get(id);
            if (tv != null) tv.setText("0");
        }
        recalculateEstimate();
    }

    /**
     * Real-time listener for student's laundry orders.
     * Matches orders where studentUid == uid OR userId == uid.
     */
    private void listenToStudentOrders(String uid) {
        ordersListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS)
            .whereEqualTo("studentUid", uid)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null || snapshots.isEmpty()) {
                    // Fallback to legacy field "userId"
                    listenToLegacyStudentOrders(uid);
                    return;
                }
                processOrderSnapshots(snapshots.getDocuments());
            });
    }

    private void listenToLegacyStudentOrders(String uid) {
        FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_LAUNDRY_ORDERS)
            .whereEqualTo("userId", uid)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    ordersEmptyText.setVisibility(View.VISIBLE);
                    ordersEmptyText.setText("Could not load orders: " + error.getMessage());
                    return;
                }
                if (snapshots != null) {
                    processOrderSnapshots(snapshots.getDocuments());
                }
            });
    }

    private void processOrderSnapshots(List<DocumentSnapshot> docs) {
        activeOrdersList.clear();
        completedOrdersList.clear();
        allOrdersList.clear();

        int totalActiveClothes = 0;
        LaundryOrder readyOrderAlert = null;

        for (DocumentSnapshot doc : docs) {
            LaundryOrder order = LaundryOrder.fromSnapshot(doc.getId(), doc.getData());
            allOrdersList.add(order);

            String status = order.getStatus() != null ? order.getStatus() : "";
            if (LaundryOrder.STATUS_COLLECTED.equalsIgnoreCase(status) ||
                LaundryOrder.STATUS_COMPLETED_LEGACY.equalsIgnoreCase(status) ||
                LaundryOrder.STATUS_CANCELLED.equalsIgnoreCase(status)) {
                completedOrdersList.add(order);
            } else {
                activeOrdersList.add(order);
                totalActiveClothes += order.getTotalItemCount();
                if (LaundryOrder.STATUS_READY.equalsIgnoreCase(status)) {
                    readyOrderAlert = order;
                }
            }
        }

        clothesCountNumber.setText(totalActiveClothes + " garments");

        // Real-Time Notification Update (Section 4)
        if (readyOrderAlert != null) {
            notificationTitleText.setText("🔔 ORDER READY FOR PICKUP!");
            notificationTitleText.setTextColor(Color.parseColor("#27AE60"));
            reminderScheduleText.setText("Order #" + readyOrderAlert.getOrderId() +
                    " is washed, ironed & ready for collection at the counter.");
        } else if (!activeOrdersList.isEmpty()) {
            LaundryOrder active = activeOrdersList.get(0);
            notificationTitleText.setText("Order #" + active.getOrderId() + " Update");
            notificationTitleText.setTextColor(Color.parseColor("#7A0019"));
            reminderScheduleText.setText("Current status: " + active.getStatus() +
                    " (" + active.getTotalItemCount() + " garments in processing).");
        } else {
            notificationTitleText.setText("Laundry Notifications");
            notificationTitleText.setTextColor(Color.parseColor("#252525"));
            reminderScheduleText.setText("No pending orders. Drop off clothes with your SAP ID to initiate a wash.");
        }

        renderCurrentTabOrders();
    }

    private View createOrderCardView(LaundryOrder order) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        // Top Row: Order ID & Status Badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvOrderId = new TextView(this);
        tvOrderId.setText("Order #" + order.getOrderId());
        tvOrderId.setTextColor(Color.parseColor("#7A0019"));
        tvOrderId.setTextSize(14);
        tvOrderId.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvOrderId.setLayoutParams(idParams);
        topRow.addView(tvOrderId);

        // Status Badge
        TextView tvStatus = new TextView(this);
        tvStatus.setText(order.getStatus());
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        applyStatusBadgeStyle(tvStatus, order.getStatus());
        topRow.addView(tvStatus);

        card.addView(topRow);

        // Created Date
        if (order.getCreatedAt() != null) {
            TextView tvDate = new TextView(this);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            tvDate.setText("Received on: " + sdf.format(order.getCreatedAt()));
            tvDate.setTextColor(Color.parseColor("#707070"));
            tvDate.setTextSize(11);
            tvDate.setPadding(0, dpToPx(4), 0, 0);
            card.addView(tvDate);
        }

        // Garment summary
        TextView tvItems = new TextView(this);
        tvItems.setText("Items: " + order.getItemsSummary());
        tvItems.setTextColor(Color.parseColor("#252525"));
        tvItems.setTextSize(13);
        tvItems.setPadding(0, dpToPx(6), 0, 0);
        card.addView(tvItems);

        // Price Row
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0, dpToPx(8), 0, 0);

        TextView tvPrice = new TextView(this);
        if (order.isConfirmed()) {
            tvPrice.setText(String.format(Locale.getDefault(), "Final Confirmed: ₹%.2f", order.getFinalPrice()));
            tvPrice.setTextColor(Color.parseColor("#27AE60"));
        } else {
            tvPrice.setText(String.format(Locale.getDefault(), "Estimated Price: ₹%.2f", order.getEstimatedPrice()));
            tvPrice.setTextColor(Color.parseColor("#7A0019"));
        }
        tvPrice.setTextSize(13);
        tvPrice.setTypeface(null, Typeface.BOLD);
        bottomRow.addView(tvPrice, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvPayment = new TextView(this);
        tvPayment.setText(order.getPaymentStatus());
        tvPayment.setTextSize(11);
        tvPayment.setTypeface(null, Typeface.BOLD);
        tvPayment.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
        if ("Paid".equalsIgnoreCase(order.getPaymentStatus())) {
            tvPayment.setBackgroundColor(Color.parseColor("#E8F5E9"));
            tvPayment.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            tvPayment.setBackgroundColor(Color.parseColor("#FFF3E0"));
            tvPayment.setTextColor(Color.parseColor("#E65100"));
        }
        bottomRow.addView(tvPayment);

        card.addView(bottomRow);

        // Admin notes if any
        if (order.getRemarks() != null && !order.getRemarks().trim().isEmpty()) {
            TextView tvNotes = new TextView(this);
            tvNotes.setText("Desk Note: " + order.getRemarks());
            tvNotes.setTextColor(Color.parseColor("#0E6251"));
            tvNotes.setTextSize(11);
            tvNotes.setPadding(0, dpToPx(4), 0, 0);
            card.addView(tvNotes);
        }

        return card;
    }

    private void applyStatusBadgeStyle(TextView tv, String status) {
        if (status == null) status = "";
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("ready")) {
            tv.setBackgroundColor(Color.parseColor("#E8F5E9"));
            tv.setTextColor(Color.parseColor("#2E7D32"));
        } else if (s.contains("wash") || s.contains("iron") || s.contains("dry") || s.contains("progress")) {
            tv.setBackgroundColor(Color.parseColor("#E3F2FD"));
            tv.setTextColor(Color.parseColor("#1565C0"));
        } else if (s.contains("collect") || s.contains("complete")) {
            tv.setBackgroundColor(Color.parseColor("#EEEEEE"));
            tv.setTextColor(Color.parseColor("#616161"));
        } else if (s.contains("cancelled")) {
            tv.setBackgroundColor(Color.parseColor("#FFEBEE"));
            tv.setTextColor(Color.parseColor("#C62828"));
        } else {
            tv.setBackgroundColor(Color.parseColor("#FFF8E7"));
            tv.setTextColor(Color.parseColor("#E67E22"));
        }
    }

    /**
     * Real-time listener for student's pending laundry requests.
     */
    private void listenToStudentRequests(String uid) {
        requestsListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS)
            .whereEqualTo("studentUid", uid)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null) {
                    return;
                }
                LaundryRequest foundPending = null;
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    LaundryRequest req = LaundryRequest.fromSnapshot(doc.getId(), doc.getData());
                    if (LaundryRequest.STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(req.getStatus())) {
                        foundPending = req;
                        break;
                    }
                }
                activePendingRequest = foundPending;
                if (activePendingRequest != null) {
                    pendingRequestContainer.setVisibility(View.VISIBLE);
                    pendingRequestIdTitle.setText("PENDING REQUEST #" + activePendingRequest.getRequestId());
                    pendingRequestStatusBadge.setText("Awaiting Admin Confirmation");
                    pendingRequestStatusBadge.setBackgroundColor(Color.parseColor("#FFF3E0"));
                    pendingRequestStatusBadge.setTextColor(Color.parseColor("#E65100"));
                    pendingRequestSummaryText.setText(String.format(Locale.getDefault(),
                            "Items: %d garments • Est. ₹%.0f",
                            activePendingRequest.getTotalClothesCount(),
                            activePendingRequest.getEstimatedPrice()));
                } else {
                    pendingRequestContainer.setVisibility(View.GONE);
                }
            });
    }

    private void initiateRequestFlow() {
        int totalQty = 0;
        double totalCost = 0.0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (LaundryPriceItem item : priceCatalog) {
            int q = itemQuantities.containsKey(item.getId()) ? itemQuantities.get(item.getId()) : 0;
            if (q > 0) {
                totalQty += q;
                double subtotal = q * item.getUnitPrice();
                totalCost += subtotal;

                Map<String, Object> map = new HashMap<>();
                map.put("id", item.getId());
                map.put("name", item.getName());
                map.put("quantity", q);
                map.put("unitPrice", item.getUnitPrice());
                map.put("subtotal", subtotal);
                items.add(map);
            }
        }

        if (totalQty <= 0) {
            Toast.makeText(this, "Please select at least 1 garment item using the '+' buttons above.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (activePendingRequest != null) {
            new AlertDialog.Builder(this)
                .setTitle("Active Pending Request Exists")
                .setMessage("You already have Request #" + activePendingRequest.getRequestId() +
                        " awaiting admin confirmation.\n\nYou can view its QR code or cancel it first.")
                .setPositiveButton("View QR", (d, w) -> openQrDisplayDialog(activePendingRequest))
                .setNeutralButton("Cancel Request", (d, w) -> confirmAndCancelPendingRequest(activePendingRequest))
                .setNegativeButton("Close", null)
                .show();
            return;
        }

        openReviewAndSubmitDialog(items, totalQty, totalCost);
    }

    private void openReviewAndSubmitDialog(List<Map<String, Object>> items, int totalQty, double totalCost) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        root.setBackgroundResource(R.drawable.bg_card);

        TextView title = new TextView(this);
        title.setText("REVIEW LAUNDRY REQUEST");
        title.setTextColor(Color.parseColor("#7A0019"));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Student: " + studentName + (studentSapId.isEmpty() ? "" : " • SAP ID: " + studentSapId));
        sub.setTextColor(Color.parseColor("#707070"));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dpToPx(4), 0, dpToPx(12));
        root.addView(sub);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        svParams.bottomMargin = dpToPx(12);
        sv.setLayoutParams(svParams);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        for (Map<String, Object> it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dpToPx(4), 0, dpToPx(4));

            TextView tvName = new TextView(this);
            tvName.setText(it.get("name") + " × " + it.get("quantity"));
            tvName.setTextColor(Color.parseColor("#252525"));
            tvName.setTextSize(13);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvName.setLayoutParams(p1);
            row.addView(tvName);

            TextView tvSub = new TextView(this);
            double subVal = (double) it.get("subtotal");
            tvSub.setText(String.format(Locale.getDefault(), "₹%.0f", subVal));
            tvSub.setTextColor(Color.parseColor("#7A0019"));
            tvSub.setTextSize(13);
            tvSub.setTypeface(null, Typeface.BOLD);
            row.addView(tvSub);

            listContainer.addView(row);
        }
        sv.addView(listContainer);
        root.addView(sv);

        // Summary Card
        LinearLayout summaryBox = new LinearLayout(this);
        summaryBox.setOrientation(LinearLayout.VERTICAL);
        summaryBox.setBackgroundColor(Color.parseColor("#FFF8E7"));
        summaryBox.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        TextView tvTotal = new TextView(this);
        tvTotal.setText(String.format(Locale.getDefault(), "Total Garments: %d  |  Estimated: ₹%.0f", totalQty, totalCost));
        tvTotal.setTextColor(Color.parseColor("#252525"));
        tvTotal.setTextSize(14);
        tvTotal.setTypeface(null, Typeface.BOLD);
        summaryBox.addView(tvTotal);

        TextView tvNotice = new TextView(this);
        tvNotice.setText("Notice: Submitting creates a pending request. The laundry desk will scan the QR to verify quantities and confirm your official order.");
        tvNotice.setTextColor(Color.parseColor("#707070"));
        tvNotice.setTextSize(11);
        tvNotice.setPadding(0, dpToPx(4), 0, 0);
        summaryBox.addView(tvNotice);

        root.addView(summaryBox);

        // Action Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dpToPx(16), 0, 0);

        Button btnBack = new Button(this);
        btnBack.setText("Back");
        btnBack.setBackgroundColor(Color.parseColor("#EEEEEE"));
        btnBack.setTextColor(Color.parseColor("#616161"));
        LinearLayout.LayoutParams pBack = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        pBack.rightMargin = dpToPx(8);
        pBack.setMarginEnd(dpToPx(8));
        btnBack.setLayoutParams(pBack);
        btnBack.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnBack);

        Button btnSubmit = new Button(this);
        btnSubmit.setText("Confirm & Generate QR");
        btnSubmit.setBackgroundColor(Color.parseColor("#7A0019"));
        btnSubmit.setTextColor(Color.WHITE);
        btnSubmit.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams pSubmit = new LinearLayout.LayoutParams(0, dpToPx(44), 1.5f);
        btnSubmit.setLayoutParams(pSubmit);

        btnSubmit.setOnClickListener(v -> {
            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting...");

            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) {
                dialog.dismiss();
                return;
            }

            String requestId = "REQ-" + (System.currentTimeMillis() % 10000000);
            LaundryRequest newRequest = new LaundryRequest();
            newRequest.setRequestId(requestId);
            newRequest.setStudentUid(u.getUid());
            newRequest.setSapId(studentSapId);
            newRequest.setStudentName(studentName);
            newRequest.setUserEmail(studentEmail);
            newRequest.setUserRoom(studentRoom);
            newRequest.setItems(items);
            newRequest.setTotalClothesCount(totalQty);
            newRequest.setEstimatedPrice(totalCost);
            newRequest.setStatus(LaundryRequest.STATUS_AWAITING_CONFIRMATION);
            newRequest.setQrToken(requestId);
            newRequest.setCreatedAt(new Date());

            FirestoreConfig.getFirestore()
                .collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS)
                .document(requestId)
                .set(newRequest.toMap())
                .addOnSuccessListener(aVoid -> {
                    resetQuantities();
                    dialog.dismiss();
                    openQrDisplayDialog(newRequest);
                    Toast.makeText(LaundryActivity.this, "Laundry request created! Present this QR at the desk.", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Confirm & Generate QR");
                    Toast.makeText(LaundryActivity.this, "Submission failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        });

        btnRow.addView(btnSubmit);
        root.addView(btnRow);

        dialog.setContentView(root);
        dialog.show();
    }

    private void openQrDisplayDialog(LaundryRequest request) {
        Dialog dialog = new Dialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        layout.setBackgroundResource(R.drawable.bg_card);

        TextView title = new TextView(this);
        title.setText("NMIMS LAUNDRY REQUEST QR");
        title.setTextColor(Color.parseColor("#7A0019"));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Present this QR code at the desk for physical item verification");
        sub.setTextColor(Color.parseColor("#707070"));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dpToPx(4), 0, dpToPx(14));
        layout.addView(sub);

        ImageView qrImage = new ImageView(this);
        Bitmap qrBitmap = QrCodeHelper.generateQrBitmap(request.getQrToken(), 450, 450);
        qrImage.setImageBitmap(qrBitmap);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dpToPx(200), dpToPx(200));
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrImage.setLayoutParams(imgParams);
        layout.addView(qrImage);

        TextView tvReq = new TextView(this);
        tvReq.setText("Request ID: " + request.getRequestId());
        tvReq.setTextColor(Color.parseColor("#252525"));
        tvReq.setTextSize(15);
        tvReq.setTypeface(null, Typeface.BOLD);
        tvReq.setGravity(Gravity.CENTER);
        tvReq.setPadding(0, dpToPx(12), 0, 0);
        layout.addView(tvReq);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Status: " + request.getStatus());
        tvStatus.setTextColor(Color.parseColor("#E65100"));
        tvStatus.setTextSize(13);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dpToPx(2), 0, 0);
        layout.addView(tvStatus);

        TextView tvSummary = new TextView(this);
        tvSummary.setText(String.format(Locale.getDefault(),
                "%d garments • Estimated: ₹%.0f",
                request.getTotalClothesCount(),
                request.getEstimatedPrice()));
        tvSummary.setTextColor(Color.parseColor("#707070"));
        tvSummary.setTextSize(13);
        tvSummary.setGravity(Gravity.CENTER);
        tvSummary.setPadding(0, dpToPx(2), 0, dpToPx(16));
        layout.addView(tvSummary);

        Button btnClose = new Button(this);
        btnClose.setText("Close");
        btnClose.setBackgroundColor(Color.parseColor("#7A0019"));
        btnClose.setTextColor(Color.WHITE);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnClose);

        dialog.setContentView(layout);
        dialog.show();
    }

    private void confirmAndCancelPendingRequest(LaundryRequest request) {
        new AlertDialog.Builder(this)
            .setTitle("Cancel Laundry Request")
            .setMessage("Are you sure you want to cancel Request #" + request.getRequestId() + "?")
            .setPositiveButton("Yes, Cancel", (d, w) -> {
                FirestoreConfig.getFirestore()
                    .collection(FirestoreConfig.COLLECTION_LAUNDRY_REQUESTS)
                    .document(request.getRequestId())
                    .update("status", LaundryRequest.STATUS_CANCELLED, "updatedAt", new Date())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Request #" + request.getRequestId() + " cancelled.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Cancellation failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            })
            .setNegativeButton("No", null)
            .show();
    }

    /**
     * Digital QR Slip for fast desk drop-off & pickup (Section 4).
     */
    private void openQrSlipDialog() {
        if (activePendingRequest != null) {
            openQrDisplayDialog(activePendingRequest);
        } else {
            initiateRequestFlow();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (pricesListener != null) pricesListener.remove();
        if (ordersListener != null) ordersListener.remove();
        if (requestsListener != null) requestsListener.remove();
        super.onDestroy();
    }
}
