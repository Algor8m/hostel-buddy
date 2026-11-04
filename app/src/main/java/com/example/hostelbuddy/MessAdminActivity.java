package com.example.hostelbuddy;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin tools for managing mess menu items in Cloud Firestore:
 * - Upload and validate full CSV with preview and atomic publication
 * - Load/edit meal items for any week/day
 * - Set normal venue and special dinners
 * - Monitor live "Now Serving" campus status
 */
public class MessAdminActivity extends AppCompatActivity {

    private Spinner weekSpinner, daySpinner, mealSpinner;
    private EditText mealItems, venueInput, specialDinnerInput, specialVenueInput;
    private TextView statusText;
    private ActivityResultLauncher<String[]> csvPicker;

    private final String[] weeks = {"Week 1", "Week 2"};
    private final String[] weekKeys = {"week1", "week2"};
    private final String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private final String[] dayKeys = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private final String[] meals = {"Breakfast", "Lunch", "Snacks", "Dinner"};
    private final String[] mealKeys = {"breakfast", "lunch", "snacks", "dinner"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isSignedIn()) return;
        setContentView(R.layout.activity_mess_admin);

        weekSpinner = findViewById(R.id.weekSpinner);
        daySpinner = findViewById(R.id.daySpinner);
        mealSpinner = findViewById(R.id.mealSpinner);
        mealItems = findViewById(R.id.mealItemsInput);
        venueInput = findViewById(R.id.venueInput);
        specialDinnerInput = findViewById(R.id.specialDinnerInput);
        specialVenueInput = findViewById(R.id.specialVenueInput);
        statusText = findViewById(R.id.messAdminStatus);

        setSpinner(weekSpinner, weeks);
        setSpinner(daySpinner, days);
        setSpinner(mealSpinner, meals);

        csvPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) handleSelectedCsv(uri);
        });

        findViewById(R.id.messAdminBack).setOnClickListener(v -> finish());
        findViewById(R.id.messAdminLogout).setOnClickListener(v -> LogoutHelper.confirmAndSignOut(this));
        findViewById(R.id.importCsvButton).setOnClickListener(v ->
            csvPicker.launch(new String[]{"text/*", "text/comma-separated-values", "application/csv", "*/*"}));

        findViewById(R.id.saveMealButton).setOnClickListener(v -> saveMeal());
        findViewById(R.id.markSpecialDinnerButton).setOnClickListener(v -> markSpecialDinner());
        findViewById(R.id.saveVenueButton).setOnClickListener(v -> saveVenue());
        findViewById(R.id.loadMealButton).setOnClickListener(v -> loadMeal());

        ensureAdminProfileInFirestore();
        statusText.setText("Ready. Choose a week, day and meal to edit, or upload a menu CSV.");
    }

    private void ensureAdminProfileInFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
            .document(user.getUid())
            .get()
            .addOnSuccessListener(snapshot -> {
                if (!snapshot.exists()) {
                    Map<String, Object> adminDoc = new HashMap<>();
                    adminDoc.put("uid", user.getUid());
                    adminDoc.put("email", user.getEmail() != null ? user.getEmail() : "");
                    adminDoc.put("fullName", user.getDisplayName() != null ? user.getDisplayName() : "Mess Administrator");
                    adminDoc.put("role", "mess_admin");
                    adminDoc.put("createdAt", FieldValue.serverTimestamp());
                    adminDoc.put("migratedFrom", "realtime_database");

                    FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_USERS)
                        .document(user.getUid())
                        .set(adminDoc, SetOptions.merge())
                        .addOnSuccessListener(v -> Log.i("MessAdminActivity", "Admin profile synchronized in Firestore."))
                        .addOnFailureListener(e -> Log.w("MessAdminActivity", "Admin profile sync note: " + e.getMessage()));
                }
            });
    }

    private boolean isSignedIn() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) return true;
        LogoutHelper.performSignOut(this);
        return false;
    }

    private void setSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private DocumentReference selectedDayDocRef() {
        int wi = Math.max(0, weekSpinner.getSelectedItemPosition());
        int di = Math.max(0, daySpinner.getSelectedItemPosition());
        String docId = weekKeys[wi] + "_" + dayKeys[di];
        return FirestoreConfig.getFirestore().collection(FirestoreConfig.COLLECTION_MESS_MENU).document(docId);
    }

    private void loadMeal() {
        String meal = mealKeys[Math.max(0, mealSpinner.getSelectedItemPosition())];
        statusText.setText("Loading menu from Firestore…");
        selectedDayDocRef().get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String value = snapshot.getString(meal);
                String venue = snapshot.getString("venue");
                String special = snapshot.getString("specialDinner");
                String specialVenue = snapshot.getString("specialDinnerVenue");
                mealItems.setText(value == null ? "" : value);
                venueInput.setText(venue == null ? "" : venue);
                specialDinnerInput.setText(special == null ? "" : special);
                specialVenueInput.setText(specialVenue == null ? "" : specialVenue);
                statusText.setText("Menu loaded from Firestore.");
            } else {
                mealItems.setText("");
                venueInput.setText("");
                specialDinnerInput.setText("");
                specialVenueInput.setText("");
                statusText.setText("No menu saved for this day yet. You can add one.");
            }
        }).addOnFailureListener(e -> {
            String errorMsg = FirebaseDiagnosticHelper.classifyError(this, e, "Loading Meal");
            statusText.setText(errorMsg);
        });
    }

    private void saveMeal() {
        String items = mealItems.getText().toString().trim();
        if (items.isEmpty()) {
            mealItems.setError("Enter the meal items.");
            return;
        }
        int wi = Math.max(0, weekSpinner.getSelectedItemPosition());
        int di = Math.max(0, daySpinner.getSelectedItemPosition());
        String key = mealKeys[Math.max(0, mealSpinner.getSelectedItemPosition())];

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (user != null) ? user.getUid() : "admin";

        Map<String, Object> updates = new HashMap<>();
        updates.put("week", weekKeys[wi]);
        updates.put("day", days[di]);
        updates.put("dayKey", dayKeys[di]);
        updates.put(key, items);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        updates.put("updatedBy", uid);
        String venue = venueInput.getText().toString().trim();
        if (!venue.isEmpty()) updates.put("venue", venue);

        statusText.setText("Saving meal to Firestore…");
        selectedDayDocRef().set(updates, SetOptions.merge())
            .addOnSuccessListener(v -> {
                statusText.setText("Meal saved. Live menu and Now Serving updated.");
                MessMenuRepository.getInstance().recomputeAndNotify();
            })
            .addOnFailureListener(e -> {
                String errorMsg = FirebaseDiagnosticHelper.classifyError(this, e, "Saving Meal");
                statusText.setText("Save failed: " + errorMsg);
            });
    }

    private void saveVenue() {
        String venue = venueInput.getText().toString().trim();
        if (venue.isEmpty()) {
            venueInput.setError("Enter a venue.");
            return;
        }
        int wi = Math.max(0, weekSpinner.getSelectedItemPosition());
        int di = Math.max(0, daySpinner.getSelectedItemPosition());
        Map<String, Object> updates = new HashMap<>();
        updates.put("week", weekKeys[wi]);
        updates.put("day", days[di]);
        updates.put("dayKey", dayKeys[di]);
        updates.put("venue", venue);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        statusText.setText("Updating venue…");
        selectedDayDocRef().set(updates, SetOptions.merge())
            .addOnSuccessListener(v -> {
                statusText.setText("Venue updated.");
                MessMenuRepository.getInstance().recomputeAndNotify();
            })
            .addOnFailureListener(e -> {
                String errorMsg = FirebaseDiagnosticHelper.classifyError(this, e, "Saving Venue");
                statusText.setText("Venue update failed: " + errorMsg);
            });
    }

    private void markSpecialDinner() {
        String special = specialDinnerInput.getText().toString().trim();
        if (special.isEmpty()) {
            specialDinnerInput.setError("Enter the special dinner menu.");
            return;
        }
        int wi = Math.max(0, weekSpinner.getSelectedItemPosition());
        int di = Math.max(0, daySpinner.getSelectedItemPosition());
        Map<String, Object> updates = new HashMap<>();
        updates.put("week", weekKeys[wi]);
        updates.put("day", days[di]);
        updates.put("dayKey", dayKeys[di]);
        updates.put("specialDinner", special);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        String venue = specialVenueInput.getText().toString().trim();
        if (!venue.isEmpty()) updates.put("specialDinnerVenue", venue);

        statusText.setText("Saving special dinner…");
        selectedDayDocRef().set(updates, SetOptions.merge())
            .addOnSuccessListener(v -> {
                statusText.setText("Special dinner saved for the selected day.");
                MessMenuRepository.getInstance().recomputeAndNotify();
            })
            .addOnFailureListener(e -> {
                String errorMsg = FirebaseDiagnosticHelper.classifyError(this, e, "Mark Special Dinner");
                statusText.setText("Could not save special dinner: " + errorMsg);
            });
    }

    private void handleSelectedCsv(Uri uri) {
        statusText.setText("Parsing and validating CSV…");

        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    runOnUiThread(() -> showValidationErrorDialog("Could not open the selected CSV file."));
                    return;
                }

                MessMenuParser.ParseResult parseResult = MessMenuParser.parseCsv(inputStream);

                runOnUiThread(() -> {
                    if (!parseResult.isValid()) {
                        showValidationErrors(parseResult.getErrors());
                        statusText.setText("CSV validation failed (" + parseResult.getErrors().size() + " errors). Fix the errors to proceed.");
                    } else {
                        showPreviewDialog(parseResult.getMenuDays(), parseResult.getWarnings());
                        statusText.setText("CSV validated. Awaiting your confirmation to publish.");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showValidationErrorDialog("Failed to read CSV: " + e.getMessage());
                    statusText.setText("CSV read error.");
                });
            }
        }).start();
    }

    private void showValidationErrors(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("The CSV could not be imported because validation failed on the following issues:\n\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("• ").append(errors.get(i)).append("\n");
        }
        sb.append("\nPlease correct the spreadsheet and upload again. No changes were committed.");

        new AlertDialog.Builder(this)
                .setTitle("CSV Validation Failed")
                .setMessage(sb.toString())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showValidationErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Validation Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPreviewDialog(List<MessMenuDay> menuDays, List<String> warnings) {
        Dialog dialog = new Dialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_menu_preview, null);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        TextView summary = view.findViewById(R.id.previewSummary);
        TextView content = view.findViewById(R.id.previewContent);
        TextView warningView = view.findViewById(R.id.previewWarning);
        Button btnCancel = view.findViewById(R.id.btnCancelPreview);
        Button btnConfirm = view.findViewById(R.id.btnConfirmPublish);

        summary.setText(String.format(Locale.getDefault(),
                "Validated %d menu days across Week 1 and Week 2.", menuDays.size()));

        StringBuilder previewText = new StringBuilder();
        String currentWeek = "";
        for (MessMenuDay day : menuDays) {
            String week = day.getWeek() != null ? day.getWeek().toUpperCase(Locale.ROOT) : "";
            if (!week.equals(currentWeek)) {
                currentWeek = week;
                previewText.append("\n═══════ ").append(currentWeek).append(" ═══════\n");
            }
            previewText.append("\n▶ ").append(day.getDay()).append(":\n");
            if (day.getBreakfast() != null && !day.getBreakfast().isEmpty()) {
                previewText.append("  • Breakfast: ").append(day.getBreakfast()).append("\n");
            }
            if (day.getLunch() != null && !day.getLunch().isEmpty()) {
                previewText.append("  • Lunch: ").append(day.getLunch()).append("\n");
            }
            if (day.getSnacks() != null && !day.getSnacks().isEmpty()) {
                previewText.append("  • Snacks: ").append(day.getSnacks()).append("\n");
            }
            if (day.getDinner() != null && !day.getDinner().isEmpty()) {
                previewText.append("  • Dinner: ").append(day.getDinner()).append("\n");
            }
            if (day.getSpecialDinner() != null && !day.getSpecialDinner().isEmpty()) {
                previewText.append("  ⭐ Special Dinner: ").append(day.getSpecialDinner()).append("\n");
            }
            if (day.getVenue() != null && !day.getVenue().isEmpty()) {
                previewText.append("  Venue: ").append(day.getVenue()).append("\n");
            }
        }
        content.setText(previewText.toString().trim());

        if (warnings != null && !warnings.isEmpty()) {
            warningView.setVisibility(View.VISIBLE);
            StringBuilder wb = new StringBuilder("Warnings:\n");
            for (String w : warnings) wb.append("• ").append(w).append("\n");
            warningView.setText(wb.toString().trim());
        } else {
            warningView.setVisibility(View.GONE);
        }

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            statusText.setText("Import cancelled. No changes were made.");
        });

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            commitMenuPublication(menuDays);
        });

        dialog.show();
    }

    private void commitMenuPublication(List<MessMenuDay> menuDays) {
        statusText.setText("Publishing " + menuDays.size() + " days atomically to Cloud Firestore…");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String adminUid = user != null ? user.getUid() : "admin";

        MessMenuRepository.getInstance().publishImportedMenu(menuDays, adminUid, new MessMenuRepository.ImportCallback() {
            @Override
            public void onSuccess(int totalPublished, @NonNull String batchId) {
                statusText.setText("Successfully published " + totalPublished + " menu days. Live for all campus students.");
                Toast.makeText(MessAdminActivity.this, "Menu published successfully! (Batch ID: " + batchId + ")", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(@NonNull String errorMessage) {
                statusText.setText("Publication failed: " + errorMessage);
                new AlertDialog.Builder(MessAdminActivity.this)
                        .setTitle("Publication Failed")
                        .setMessage("Error writing batch to Firestore:\n" + errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }
}
