package com.example.hostelbuddy;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Modern, card-based Mess Menu screen matching the NMIMS UI inspiration.
 * Allows students to swipe or step through days of the 2-week rotating schedule.
 */
public class MessMenuActivity extends AppCompatActivity {

    private TextView txtWeekLabel, txtDayLabel;
    private TextView txtBreakfastItems, txtLunchItems, txtSnacksItems, txtDinnerItems;
    private TextView badgeSpecialDinnerNote, txtMessVenueInfo;

    private ListenerRegistration menuListener;

    private static final String[] WEEKS = {"week1", "week2"};
    private static final String[] WEEK_DISPLAY = {"Week 1", "Week 2"};
    private static final String[] DAYS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private static final String[] DAY_DISPLAY = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private int activeWeekIdx = 0;
    private int activeDayIdx = 0;

    private final Map<String, MessMenuDay> menuDaysMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mess_menu);

        findViewById(R.id.messBackButton).setOnClickListener(v -> finish());

        txtWeekLabel = findViewById(R.id.txtWeekLabel);
        txtDayLabel = findViewById(R.id.txtDayLabel);
        txtBreakfastItems = findViewById(R.id.txtBreakfastItems);
        txtLunchItems = findViewById(R.id.txtLunchItems);
        txtSnacksItems = findViewById(R.id.txtSnacksItems);
        txtDinnerItems = findViewById(R.id.txtDinnerItems);
        badgeSpecialDinnerNote = findViewById(R.id.badgeSpecialDinnerNote);
        txtMessVenueInfo = findViewById(R.id.txtMessVenueInfo);

        findViewById(R.id.btnPrevDay).setOnClickListener(v -> navigatePreviousDay());
        findViewById(R.id.btnNextDay).setOnClickListener(v -> navigateNextDay());

        initCurrentDate();
        listenToMessMenu();
    }

    private void initCurrentDate() {
        Calendar cal = NowServingService.getCampusCalendar();
        String currentWeek = NowServingService.getCurrentWeekKey(cal);
        String currentDay = NowServingService.getCurrentDayKey(cal);

        activeWeekIdx = "week2".equalsIgnoreCase(currentWeek) ? 1 : 0;
        for (int i = 0; i < DAYS.length; i++) {
            if (DAYS[i].equalsIgnoreCase(currentDay)) {
                activeDayIdx = i;
                break;
            }
        }
        updateDateHeader();
    }

    private void navigatePreviousDay() {
        activeDayIdx--;
        if (activeDayIdx < 0) {
            activeDayIdx = DAYS.length - 1;
            activeWeekIdx = (activeWeekIdx == 0) ? 1 : 0;
        }
        updateDateHeader();
        renderCurrentDayMenu();
    }

    private void navigateNextDay() {
        activeDayIdx++;
        if (activeDayIdx >= DAYS.length) {
            activeDayIdx = 0;
            activeWeekIdx = (activeWeekIdx == 0) ? 1 : 0;
        }
        updateDateHeader();
        renderCurrentDayMenu();
    }

    private void updateDateHeader() {
        txtWeekLabel.setText(WEEK_DISPLAY[activeWeekIdx]);
        txtDayLabel.setText(DAY_DISPLAY[activeDayIdx]);
    }

    private void listenToMessMenu() {
        menuListener = FirestoreConfig.getFirestore()
            .collection(FirestoreConfig.COLLECTION_MESS_MENU)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    txtBreakfastItems.setText("Could not load menu: " + error.getMessage());
                    return;
                }

                menuDaysMap.clear();
                if (snapshots != null) {
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        MessMenuDay day = MessMenuDay.fromSnapshot(doc.getId(), doc.getData());
                        String key = (day.getWeek() + "_" + day.getDayKey()).toLowerCase(Locale.ROOT);
                        menuDaysMap.put(key, day);
                    }
                }

                renderCurrentDayMenu();
            });
    }

    private void renderCurrentDayMenu() {
        String key = (WEEKS[activeWeekIdx] + "_" + DAYS[activeDayIdx]).toLowerCase(Locale.ROOT);
        MessMenuDay day = menuDaysMap.get(key);

        if (day == null) {
            txtBreakfastItems.setText("• No menu published for this day yet.");
            txtLunchItems.setText("• No menu published for this day yet.");
            txtSnacksItems.setText("• No menu published for this day yet.");
            txtDinnerItems.setText("• No menu published for this day yet.");
            badgeSpecialDinnerNote.setVisibility(View.GONE);
            txtMessVenueInfo.setText("Serving Venue: Main Mess • NMIMS Hyderabad Campus");
            return;
        }

        txtBreakfastItems.setText(formatBulletPoints(day.getBreakfast()));
        txtLunchItems.setText(formatBulletPoints(day.getLunch()));
        txtSnacksItems.setText(formatBulletPoints(day.getSnacks()));
        txtDinnerItems.setText(formatBulletPoints(day.getDinner()));

        if (day.getSpecialDinner() != null && !day.getSpecialDinner().trim().isEmpty()) {
            badgeSpecialDinnerNote.setVisibility(View.VISIBLE);
            String v = (day.getSpecialDinnerVenue() != null && !day.getSpecialDinnerVenue().isEmpty())
                    ? " (" + day.getSpecialDinnerVenue() + ")" : "";
            badgeSpecialDinnerNote.setText("⭐ SPECIAL DINNER: " + day.getSpecialDinner() + v);
        } else {
            badgeSpecialDinnerNote.setVisibility(View.GONE);
        }

        String venue = (day.getVenue() != null && !day.getVenue().isEmpty()) ? day.getVenue() : "Main Mess";
        txtMessVenueInfo.setText("Serving Venue: " + venue + " • NMIMS Hyderabad Campus");
    }

    private String formatBulletPoints(String raw) {
        if (raw == null || raw.trim().isEmpty() || "-".equals(raw.trim())) {
            return "• Not scheduled / Available at counter";
        }
        String[] items = raw.split("[,;\\n]+");
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(trimmed);
            }
        }
        return sb.length() > 0 ? sb.toString() : "• " + raw;
    }

    @Override
    protected void onDestroy() {
        if (menuListener != null) {
            menuListener.remove();
        }
        super.onDestroy();
    }
}
