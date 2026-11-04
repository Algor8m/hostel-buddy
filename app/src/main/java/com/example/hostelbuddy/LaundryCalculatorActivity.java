package com.example.hostelbuddy;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

public class LaundryCalculatorActivity extends AppCompatActivity {

    private TextView syncBadge;
    private TextView washRateLabel, ironRateLabel, dryRateLabel, bedsheetRateLabel;
    private TextView washQtyText, ironQtyText, dryQtyText, bedsheetQtyText;
    private TextView washSubtotalText, ironSubtotalText, drySubtotalText, bedsheetSubtotalText;
    private TextView calcTotalItems, calcGrandTotal;
    private Button btnBackToTracking, btnResetCalculator;

    // Rates with safe hostel defaults
    private double washRate = 10.0;
    private double ironRate = 5.0;
    private double dryRate = 60.0;
    private double bedsheetRate = 25.0;

    private int washCount = 0;
    private int ironCount = 0;
    private int dryCount = 0;
    private int bedsheetCount = 0;

    private ListenerRegistration ratesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_laundry_calculator);

        findViewById(R.id.calcBackButton).setOnClickListener(v -> finish());

        syncBadge = findViewById(R.id.calcSyncBadge);
        washRateLabel = findViewById(R.id.washRateLabel);
        ironRateLabel = findViewById(R.id.ironRateLabel);
        dryRateLabel = findViewById(R.id.dryRateLabel);
        bedsheetRateLabel = findViewById(R.id.bedsheetRateLabel);

        washQtyText = findViewById(R.id.washQtyText);
        ironQtyText = findViewById(R.id.ironQtyText);
        dryQtyText = findViewById(R.id.dryQtyText);
        bedsheetQtyText = findViewById(R.id.bedsheetQtyText);

        washSubtotalText = findViewById(R.id.washSubtotalText);
        ironSubtotalText = findViewById(R.id.ironSubtotalText);
        drySubtotalText = findViewById(R.id.drySubtotalText);
        bedsheetSubtotalText = findViewById(R.id.bedsheetSubtotalText);

        calcTotalItems = findViewById(R.id.calcTotalItems);
        calcGrandTotal = findViewById(R.id.calcGrandTotal);

        btnBackToTracking = findViewById(R.id.btnBackToTracking);
        btnResetCalculator = findViewById(R.id.btnResetCalculator);

        setupSteppers();
        listenToRates();
        recalculate();

        btnResetCalculator.setOnClickListener(v -> resetQuantities());
        if (btnBackToTracking != null) {
            btnBackToTracking.setOnClickListener(v -> finish());
        }
    }

    private void setupSteppers() {
        // Wash & Fold
        findViewById(R.id.btnWashPlus).setOnClickListener(v -> {
            washCount++;
            washQtyText.setText(String.valueOf(washCount));
            recalculate();
        });
        findViewById(R.id.btnWashMinus).setOnClickListener(v -> {
            if (washCount > 0) {
                washCount--;
                washQtyText.setText(String.valueOf(washCount));
                recalculate();
            }
        });

        // Steam Ironing
        findViewById(R.id.btnIronPlus).setOnClickListener(v -> {
            ironCount++;
            ironQtyText.setText(String.valueOf(ironCount));
            recalculate();
        });
        findViewById(R.id.btnIronMinus).setOnClickListener(v -> {
            if (ironCount > 0) {
                ironCount--;
                ironQtyText.setText(String.valueOf(ironCount));
                recalculate();
            }
        });

        // Dry Cleaning
        findViewById(R.id.btnDryPlus).setOnClickListener(v -> {
            dryCount++;
            dryQtyText.setText(String.valueOf(dryCount));
            recalculate();
        });
        findViewById(R.id.btnDryMinus).setOnClickListener(v -> {
            if (dryCount > 0) {
                dryCount--;
                dryQtyText.setText(String.valueOf(dryCount));
                recalculate();
            }
        });

        // Bedsheets
        findViewById(R.id.btnBedsheetPlus).setOnClickListener(v -> {
            bedsheetCount++;
            bedsheetQtyText.setText(String.valueOf(bedsheetCount));
            recalculate();
        });
        findViewById(R.id.btnBedsheetMinus).setOnClickListener(v -> {
            if (bedsheetCount > 0) {
                bedsheetCount--;
                bedsheetQtyText.setText(String.valueOf(bedsheetCount));
                recalculate();
            }
        });
    }

    private void listenToRates() {
        DocumentReference ratesRef = FirestoreConfig.getFirestore()
                .collection(FirestoreConfig.COLLECTION_LAUNDRY_RATES)
                .document(FirestoreConfig.DOC_LAUNDRY_DEFAULT_RATES);

        ratesListener = ratesRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                syncBadge.setText("○ Using offline fallback rates");
                syncBadge.setTextColor(Color.parseColor("#B3261E"));
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Double w = snapshot.getDouble("wash");
                Double i = snapshot.getDouble("ironing");
                Double d = snapshot.getDouble("dryCleaning");
                Double b = snapshot.getDouble("bedsheet");

                if (w != null) washRate = w;
                if (i != null) ironRate = i;
                if (d != null) dryRate = d;
                if (b != null) bedsheetRate = b;

                boolean isCache = snapshot.getMetadata().isFromCache();
                if (isCache) {
                    syncBadge.setText("○ Showing cached rates from Firestore");
                    syncBadge.setTextColor(Color.parseColor("#856404"));
                } else {
                    syncBadge.setText("● Live rates confirmed from Firestore");
                    syncBadge.setTextColor(Color.parseColor("#0E6251"));
                }
            } else {
                syncBadge.setText("● Standard campus rate card active");
                syncBadge.setTextColor(Color.parseColor("#0E6251"));
            }

            updateRateLabels();
            recalculate();
        });
    }

    private void updateRateLabels() {
        washRateLabel.setText(String.format(Locale.getDefault(), "₹%.2f / item", washRate));
        ironRateLabel.setText(String.format(Locale.getDefault(), "₹%.2f / item", ironRate));
        dryRateLabel.setText(String.format(Locale.getDefault(), "₹%.2f / item", dryRate));
        bedsheetRateLabel.setText(String.format(Locale.getDefault(), "₹%.2f / item", bedsheetRate));
    }

    private void recalculate() {
        double washSubtotal = washCount * washRate;
        double ironSubtotal = ironCount * ironRate;
        double drySubtotal = dryCount * dryRate;
        double bedsheetSubtotal = bedsheetCount * bedsheetRate;

        int totalItems = washCount + ironCount + dryCount + bedsheetCount;
        double grandTotal = washSubtotal + ironSubtotal + drySubtotal + bedsheetSubtotal;

        washSubtotalText.setText(String.format(Locale.getDefault(), "₹%.2f", washSubtotal));
        ironSubtotalText.setText(String.format(Locale.getDefault(), "₹%.2f", ironSubtotal));
        drySubtotalText.setText(String.format(Locale.getDefault(), "₹%.2f", drySubtotal));
        bedsheetSubtotalText.setText(String.format(Locale.getDefault(), "₹%.2f", bedsheetSubtotal));

        calcTotalItems.setText(totalItems + (totalItems == 1 ? " item" : " items"));
        calcGrandTotal.setText(String.format(Locale.getDefault(), "₹%.2f", grandTotal));
    }

    private void resetQuantities() {
        washCount = 0;
        ironCount = 0;
        dryCount = 0;
        bedsheetCount = 0;

        washQtyText.setText("0");
        ironQtyText.setText("0");
        dryQtyText.setText("0");
        bedsheetQtyText.setText("0");

        recalculate();
    }

    @Override
    protected void onDestroy() {
        if (ratesListener != null) {
            ratesListener.remove();
        }
        super.onDestroy();
    }
}
