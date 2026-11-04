package com.example.hostelbuddy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shared service for evaluating what meal is currently being served or upcoming.
 * Uses the official campus timezone (Asia/Kolkata, UTC+05:30) and respects
 * authoritative meal schedules and midnight boundaries.
 */
public final class NowServingService {

    public static final String CAMPUS_TIMEZONE = "Asia/Kolkata";

    public enum ServingState {
        NOW_SERVING,
        UPCOMING,
        FINISHED
    }

    public static class NowServingInfo implements Serializable {
        public ServingState state = ServingState.UPCOMING;
        public String mealKey = "breakfast";
        public String mealName = "Breakfast";
        public String servingHours = "08:00 – 09:30";
        public String foodItems = "No menu items configured.";
        public String venue = "Main Mess";
        public boolean isSpecialDinner = false;
        public String specialDinnerItems = "";
        public String specialDinnerVenue = "";
        public String nextMealTitle = "";
        public String timeStatusText = "";
        public String weekAndDayLabel = "";
        public boolean isLiveFromServer = false;

        public boolean isNowServing() {
            return state == ServingState.NOW_SERVING;
        }

        public boolean isFinished() {
            return state == ServingState.FINISHED;
        }
    }

    private NowServingService() { }

    public static Calendar getCampusCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone(CAMPUS_TIMEZONE));
    }

    public static String getCurrentDayKey(Calendar cal) {
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        switch (dayOfWeek) {
            case Calendar.MONDAY: return "monday";
            case Calendar.TUESDAY: return "tuesday";
            case Calendar.WEDNESDAY: return "wednesday";
            case Calendar.THURSDAY: return "thursday";
            case Calendar.FRIDAY: return "friday";
            case Calendar.SATURDAY: return "saturday";
            case Calendar.SUNDAY: return "sunday";
            default: return "monday";
        }
    }

    public static String getNextDayKey(String currentDayKey) {
        switch (currentDayKey) {
            case "monday": return "tuesday";
            case "tuesday": return "wednesday";
            case "wednesday": return "thursday";
            case "thursday": return "friday";
            case "friday": return "saturday";
            case "saturday": return "sunday";
            case "sunday": return "monday";
            default: return "monday";
        }
    }

    public static String getCurrentWeekKey(Calendar cal) {
        int weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
        return (weekOfYear % 2 != 0) ? "week1" : "week2";
    }

    @NonNull
    public static NowServingInfo computeNowServing(@Nullable MessMenuDay todayMenu, @Nullable MessMenuDay tomorrowMenu, boolean isLive) {
        NowServingInfo info = new NowServingInfo();
        info.isLiveFromServer = isLive;

        Calendar now = getCampusCalendar();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int currentMinutes = hour * 60 + minute;

        String dayKey = getCurrentDayKey(now);
        String weekKey = getCurrentWeekKey(now);
        String dayDisplay = MessMenuParser.titleCase(dayKey);
        String weekDisplay = "week1".equals(weekKey) ? "Week 1" : "Week 2";
        info.weekAndDayLabel = weekDisplay + " • " + dayDisplay;

        // Campus standard meal boundaries (in minutes from midnight)
        // Breakfast: 08:00 (480) - 09:30 (570)
        // Lunch:     12:30 (750) - 14:30 (870)
        // Snacks:    17:30 (1050) - 18:30 (1110)
        // Dinner:    20:00 (1200) - 21:30 (1290)

        int breakfastStart = 8 * 60;
        int breakfastEnd = 9 * 60 + 30;

        int lunchStart = 12 * 60 + 30;
        int lunchEnd = 14 * 60 + 30;

        int snacksStart = 17 * 60 + 30;
        int snacksEnd = 18 * 60 + 30;

        int dinnerStart = 20 * 60;
        int dinnerEnd = 21 * 60 + 30;

        if (currentMinutes < breakfastStart) {
            // Before breakfast (midnight - 8:00 AM)
            info.state = ServingState.UPCOMING;
            info.mealKey = "breakfast";
            info.mealName = "Breakfast";
            info.servingHours = (todayMenu != null && todayMenu.getBreakfastHours() != null && !todayMenu.getBreakfastHours().isEmpty())
                    ? todayMenu.getBreakfastHours() : "08:00 – 09:30";
            info.timeStatusText = "Starts in " + formatDuration(breakfastStart - currentMinutes);
            info.nextMealTitle = "Next: Breakfast at 08:00";
            applyMenuData(info, todayMenu, "breakfast");

        } else if (currentMinutes <= breakfastEnd) {
            // Breakfast active
            info.state = ServingState.NOW_SERVING;
            info.mealKey = "breakfast";
            info.mealName = "Breakfast";
            info.servingHours = (todayMenu != null && todayMenu.getBreakfastHours() != null && !todayMenu.getBreakfastHours().isEmpty())
                    ? todayMenu.getBreakfastHours() : "08:00 – 09:30";
            info.timeStatusText = "Serving ends in " + formatDuration(breakfastEnd - currentMinutes);
            info.nextMealTitle = "Next: Lunch at 12:30";
            applyMenuData(info, todayMenu, "breakfast");

        } else if (currentMinutes < lunchStart) {
            // Between breakfast and lunch (9:30 AM - 12:30 PM)
            info.state = ServingState.UPCOMING;
            info.mealKey = "lunch";
            info.mealName = "Lunch";
            info.servingHours = (todayMenu != null && todayMenu.getLunchHours() != null && !todayMenu.getLunchHours().isEmpty())
                    ? todayMenu.getLunchHours() : "12:30 – 14:30";
            info.timeStatusText = "Starts in " + formatDuration(lunchStart - currentMinutes);
            info.nextMealTitle = "Next: Lunch at 12:30";
            applyMenuData(info, todayMenu, "lunch");

        } else if (currentMinutes <= lunchEnd) {
            // Lunch active
            info.state = ServingState.NOW_SERVING;
            info.mealKey = "lunch";
            info.mealName = "Lunch";
            info.servingHours = (todayMenu != null && todayMenu.getLunchHours() != null && !todayMenu.getLunchHours().isEmpty())
                    ? todayMenu.getLunchHours() : "12:30 – 14:30";
            info.timeStatusText = "Serving ends in " + formatDuration(lunchEnd - currentMinutes);
            info.nextMealTitle = "Next: Snacks at 17:30";
            applyMenuData(info, todayMenu, "lunch");

        } else if (currentMinutes < snacksStart) {
            // Between lunch and snacks (2:30 PM - 5:30 PM)
            info.state = ServingState.UPCOMING;
            info.mealKey = "snacks";
            info.mealName = "Snacks";
            info.servingHours = (todayMenu != null && todayMenu.getSnacksHours() != null && !todayMenu.getSnacksHours().isEmpty())
                    ? todayMenu.getSnacksHours() : "17:30 – 18:30";
            info.timeStatusText = "Starts in " + formatDuration(snacksStart - currentMinutes);
            info.nextMealTitle = "Next: Evening Snacks at 17:30";
            applyMenuData(info, todayMenu, "snacks");

        } else if (currentMinutes <= snacksEnd) {
            // Snacks active
            info.state = ServingState.NOW_SERVING;
            info.mealKey = "snacks";
            info.mealName = "Snacks";
            info.servingHours = (todayMenu != null && todayMenu.getSnacksHours() != null && !todayMenu.getSnacksHours().isEmpty())
                    ? todayMenu.getSnacksHours() : "17:30 – 18:30";
            info.timeStatusText = "Serving ends in " + formatDuration(snacksEnd - currentMinutes);
            info.nextMealTitle = "Next: Dinner at 20:00";
            applyMenuData(info, todayMenu, "snacks");

        } else if (currentMinutes < dinnerStart) {
            // Between snacks and dinner (6:30 PM - 8:00 PM)
            info.state = ServingState.UPCOMING;
            info.mealKey = "dinner";
            info.mealName = "Dinner";
            info.servingHours = (todayMenu != null && todayMenu.getDinnerHours() != null && !todayMenu.getDinnerHours().isEmpty())
                    ? todayMenu.getDinnerHours() : "20:00 – 21:30";
            info.timeStatusText = "Starts in " + formatDuration(dinnerStart - currentMinutes);
            info.nextMealTitle = "Next: Dinner at 20:00";
            applyMenuData(info, todayMenu, "dinner");

        } else if (currentMinutes <= dinnerEnd) {
            // Dinner active
            info.state = ServingState.NOW_SERVING;
            info.mealKey = "dinner";
            info.mealName = "Dinner";
            info.servingHours = (todayMenu != null && todayMenu.getDinnerHours() != null && !todayMenu.getDinnerHours().isEmpty())
                    ? todayMenu.getDinnerHours() : "20:00 – 21:30";
            info.timeStatusText = "Serving ends in " + formatDuration(dinnerEnd - currentMinutes);
            info.nextMealTitle = "Next: Tomorrow's Breakfast at 08:00";
            applyMenuData(info, todayMenu, "dinner");

        } else {
            // After dinner (9:30 PM - midnight)
            info.state = ServingState.FINISHED;
            info.mealKey = "dinner";
            info.mealName = "Dinner (Finished)";
            info.servingHours = "Completed for today";
            info.timeStatusText = "Dinner concluded at 21:30";
            info.nextMealTitle = "Next: Tomorrow's Breakfast at 08:00";
            if (tomorrowMenu != null) {
                info.foodItems = "Tomorrow's Breakfast: " + (tomorrowMenu.getBreakfast() != null ? tomorrowMenu.getBreakfast() : "Regular Breakfast");
            } else {
                applyMenuData(info, todayMenu, "dinner");
            }
        }

        return info;
    }

    private static void applyMenuData(NowServingInfo info, MessMenuDay day, String mealKey) {
        if (day == null) {
            info.foodItems = "No menu items saved for this day yet.";
            info.venue = "Main Mess";
            return;
        }

        String items = day.getMealItems(mealKey);
        info.foodItems = (items != null && !items.trim().isEmpty()) ? items : "No items listed for this meal.";
        info.venue = (day.getVenue() != null && !day.getVenue().trim().isEmpty()) ? day.getVenue() : "Main Mess";

        if ("dinner".equalsIgnoreCase(mealKey) && day.getSpecialDinner() != null && !day.getSpecialDinner().trim().isEmpty()) {
            info.isSpecialDinner = true;
            info.specialDinnerItems = day.getSpecialDinner();
            info.specialDinnerVenue = (day.getSpecialDinnerVenue() != null && !day.getSpecialDinnerVenue().trim().isEmpty())
                    ? day.getSpecialDinnerVenue() : info.venue;
        }
    }

    private static String formatDuration(int minutes) {
        if (minutes <= 0) return "just now";
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0 && m > 0) return h + "h " + m + "m";
        if (h > 0) return h + "h";
        return m + "m";
    }
}
