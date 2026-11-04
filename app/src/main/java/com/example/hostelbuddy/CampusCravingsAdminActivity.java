package com.example.hostelbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CRUD tools for Campus Cravings menu stored in Cloud Firestore.
 */
public class CampusCravingsAdminActivity extends AppCompatActivity {
    private EditText numberInput, nameInput, priceInput;
    private Spinner categorySpinner;
    private CheckBox availableCheck;
    private TextView statusText;
    private LinearLayout itemsContainer;
    private CollectionReference itemsRef;
    private ListenerRegistration itemsListener;
    private String editingDocId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            LogoutHelper.performSignOut(this);
            return;
        }

        setContentView(R.layout.activity_campus_cravings_admin);
        numberInput = findViewById(R.id.itemNumberInput);
        nameInput = findViewById(R.id.itemNameInput);
        priceInput = findViewById(R.id.itemPriceInput);
        categorySpinner = findViewById(R.id.itemCategorySpinner);
        availableCheck = findViewById(R.id.itemAvailableCheck);
        statusText = findViewById(R.id.cravingsAdminStatus);
        itemsContainer = findViewById(R.id.cravingsItemsContainer);

        ArrayAdapter<String> categories = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, new String[]{"Veg", "Non Veg"});
        categories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categories);
        categorySpinner.setPrompt("Select category");
        availableCheck.setChecked(true);

        itemsRef = FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_FOOD_TRUCK_ITEMS);

        findViewById(R.id.cravingsAdminBack).setOnClickListener(v -> finish());
        findViewById(R.id.cravingsAdminLogout).setOnClickListener(v -> LogoutHelper.confirmAndSignOut(this));
        findViewById(R.id.saveCravingsItemButton).setOnClickListener(v -> saveItem());
        findViewById(R.id.clearCravingsFormButton).setOnClickListener(v -> clearForm());

        listenForItems();
    }

    private void listenForItems() {
        itemsListener = itemsRef.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                statusText.setText("Could not load items: " + error.getMessage());
                return;
            }
            itemsContainer.removeAllViews();
            if (snapshots == null || snapshots.isEmpty()) {
                TextView empty = new TextView(CampusCravingsAdminActivity.this);
                empty.setText("No items yet. Add the first Campus Cravings item above.");
                empty.setTextColor(getColor(R.color.slate));
                empty.setPadding(8, 16, 8, 16);
                itemsContainer.addView(empty);
                return;
            }
            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                addItemRow(doc);
            }
        });
    }

    private void addItemRow(DocumentSnapshot doc) {
        String docId = doc.getId();
        String number = doc.getString("itemNumber");
        if (number == null) number = "";
        String name = doc.getString("name");
        if (name == null) name = "";
        String category = doc.getString("category");
        if (category == null) category = "";
        Double price = doc.getDouble("price");
        Boolean available = doc.getBoolean("available");
        if (available == null) available = true;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);
        itemsContainer.addView(card, cardParams);

        TextView title = new TextView(this);
        title.setText((number.isEmpty() ? "" : number + " • ") + (name.isEmpty() ? "(unnamed item)" : name));
        title.setTextSize(17);
        title.setTextColor(getColor(R.color.navy));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView detail = new TextView(this);
        detail.setText("₹" + (price == null ? "—" : String.format(Locale.getDefault(), "%.2f", price)) + " • " +
            (category.isEmpty() ? "Category not set" : category) + " • " +
            (!available ? "NOT AVAILABLE" : "AVAILABLE"));
        detail.setTextColor(!available ? getColor(R.color.error_red) : getColor(R.color.slate));
        detail.setPadding(0, dp(4), 0, dp(8));
        card.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        card.addView(actions);

        Button edit = smallButton("Edit");
        Button toggle = smallButton(!available ? "Mark available" : "Mark unavailable");
        Button remove = smallButton("Remove");
        actions.addView(edit);
        actions.addView(toggle);
        actions.addView(remove);

        final boolean currentAvailability = available;
        final String itemName = name;

        edit.setOnClickListener(v -> fillForm(docId, doc));
        toggle.setOnClickListener(v -> {
            itemsRef.document(docId).update("available", !currentAvailability)
                .addOnSuccessListener(x -> statusText.setText("Availability updated."))
                .addOnFailureListener(e -> statusText.setText("Update failed: " + e.getMessage()));
        });

        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Remove item?")
            .setMessage("Remove " + (itemName.isEmpty() ? "this item" : itemName) + " from the Campus Cravings menu?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove", (d, w) -> {
                itemsRef.document(docId).delete()
                    .addOnSuccessListener(x -> statusText.setText("Item removed."))
                    .addOnFailureListener(e -> statusText.setText("Remove failed: " + e.getMessage()));
            })
            .show());
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private void fillForm(String docId, DocumentSnapshot doc) {
        editingDocId = docId;
        String number = doc.getString("itemNumber");
        numberInput.setText(number == null ? "" : number);
        String name = doc.getString("name");
        nameInput.setText(name == null ? "" : name);
        Double price = doc.getDouble("price");
        priceInput.setText(price == null ? "" : String.valueOf(price));
        String category = doc.getString("category");
        categorySpinner.setSelection(category != null && "non veg".equals(category.toLowerCase(Locale.ROOT)) ? 1 : 0);
        Boolean available = doc.getBoolean("available");
        availableCheck.setChecked(available == null || available);
        findViewById(R.id.saveCravingsItemButton).setEnabled(true);
        statusText.setText("Editing item. Save to apply changes.");
        nameInput.requestFocus();
    }

    private void saveItem() {
        String number = numberInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();

        if (name.isEmpty()) { nameInput.setError("Enter an item name."); return; }
        if (priceText.isEmpty()) { priceInput.setError("Enter a price."); return; }

        double price;
        try {
            price = Double.parseDouble(priceText);
            if (price < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            priceInput.setError("Enter a valid non-negative price.");
            return;
        }

        DocumentReference target;
        if (editingDocId != null) {
            target = itemsRef.document(editingDocId);
        } else if (!number.isEmpty()) {
            target = itemsRef.document("item_" + number.replaceAll("[^A-Za-z0-9_-]", "_"));
        } else {
            target = itemsRef.document();
        }

        Map<String, Object> values = new HashMap<>();
        values.put("itemNumber", number.isEmpty() ? target.getId() : number);
        values.put("name", name);
        values.put("price", price);
        values.put("category", categorySpinner.getSelectedItemPosition() == 1 ? "Non Veg" : "Veg");
        values.put("available", availableCheck.isChecked());
        values.put("updatedAt", FieldValue.serverTimestamp());

        statusText.setText("Saving item to Firestore…");
        target.set(values, SetOptions.merge())
            .addOnSuccessListener(v -> {
                statusText.setText(editingDocId == null ? "Item added to Firestore." : "Item updated in Firestore.");
                clearForm();
            })
            .addOnFailureListener(e -> statusText.setText("Save failed: " + e.getMessage()));
    }

    private void clearForm() {
        editingDocId = null;
        numberInput.setText("");
        nameInput.setText("");
        priceInput.setText("");
        categorySpinner.setSelection(0);
        availableCheck.setChecked(true);
        statusText.setText("Ready to add an item.");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (itemsListener != null) {
            itemsListener.remove();
        }
        super.onDestroy();
    }
}
