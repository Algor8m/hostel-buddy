package com.example.hostelbuddy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Model representing a single day's mess menu across all meals.
 */
public class MessMenuDay implements Serializable {

    private String week; // "week1" or "week2"
    private String day;  // "Monday", "Tuesday", etc.
    private String dayKey; // "monday", "tuesday", etc.
    private String breakfast;
    private String lunch;
    private String snacks;
    private String dinner;
    private String venue;
    private String specialDinner;
    private String specialDinnerVenue;
    private String breakfastHours;
    private String lunchHours;
    private String snacksHours;
    private String dinnerHours;

    public MessMenuDay() { }

    public MessMenuDay(String week, String day, String dayKey) {
        this.week = week;
        this.day = day;
        this.dayKey = dayKey;
    }

    public String getDocumentId() {
        return (week != null ? week.toLowerCase() : "week1") + "_" + (dayKey != null ? dayKey.toLowerCase() : "monday");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("week", week != null ? week : "week1");
        map.put("day", day != null ? day : "");
        map.put("dayKey", dayKey != null ? dayKey : "");
        if (breakfast != null) map.put("breakfast", breakfast);
        if (lunch != null) map.put("lunch", lunch);
        if (snacks != null) map.put("snacks", snacks);
        if (dinner != null) map.put("dinner", dinner);
        if (venue != null && !venue.trim().isEmpty()) map.put("venue", venue);
        if (specialDinner != null && !specialDinner.trim().isEmpty()) map.put("specialDinner", specialDinner);
        if (specialDinnerVenue != null && !specialDinnerVenue.trim().isEmpty()) map.put("specialDinnerVenue", specialDinnerVenue);
        if (breakfastHours != null) map.put("breakfastHours", breakfastHours);
        if (lunchHours != null) map.put("lunchHours", lunchHours);
        if (snacksHours != null) map.put("snacksHours", snacksHours);
        if (dinnerHours != null) map.put("dinnerHours", dinnerHours);
        return map;
    }

    public static MessMenuDay fromSnapshot(String docId, Map<String, Object> data) {
        MessMenuDay day = new MessMenuDay();
        if (data == null) return day;

        day.setWeek((String) data.get("week"));
        day.setDay((String) data.get("day"));
        day.setDayKey((String) data.get("dayKey"));
        day.setBreakfast((String) data.get("breakfast"));
        day.setLunch((String) data.get("lunch"));
        day.setSnacks((String) data.get("snacks"));
        day.setDinner((String) data.get("dinner"));
        day.setVenue((String) data.get("venue"));
        day.setSpecialDinner((String) data.get("specialDinner"));
        day.setSpecialDinnerVenue((String) data.get("specialDinnerVenue"));
        day.setBreakfastHours((String) data.get("breakfastHours"));
        day.setLunchHours((String) data.get("lunchHours"));
        day.setSnacksHours((String) data.get("snacksHours"));
        day.setDinnerHours((String) data.get("dinnerHours"));

        return day;
    }

    // Getters and Setters
    public String getWeek() { return week; }
    public void setWeek(String week) { this.week = week; }

    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }

    public String getDayKey() { return dayKey; }
    public void setDayKey(String dayKey) { this.dayKey = dayKey; }

    public String getBreakfast() { return breakfast; }
    public void setBreakfast(String breakfast) { this.breakfast = breakfast; }

    public String getLunch() { return lunch; }
    public void setLunch(String lunch) { this.lunch = lunch; }

    public String getSnacks() { return snacks; }
    public void setSnacks(String snacks) { this.snacks = snacks; }

    public String getDinner() { return dinner; }
    public void setDinner(String dinner) { this.dinner = dinner; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getSpecialDinner() { return specialDinner; }
    public void setSpecialDinner(String specialDinner) { this.specialDinner = specialDinner; }

    public String getSpecialDinnerVenue() { return specialDinnerVenue; }
    public void setSpecialDinnerVenue(String specialDinnerVenue) { this.specialDinnerVenue = specialDinnerVenue; }

    public String getBreakfastHours() { return breakfastHours; }
    public void setBreakfastHours(String breakfastHours) { this.breakfastHours = breakfastHours; }

    public String getLunchHours() { return lunchHours; }
    public void setLunchHours(String lunchHours) { this.lunchHours = lunchHours; }

    public String getSnacksHours() { return snacksHours; }
    public void setSnacksHours(String snacksHours) { this.snacksHours = snacksHours; }

    public String getDinnerHours() { return dinnerHours; }
    public void setDinnerHours(String dinnerHours) { this.dinnerHours = dinnerHours; }

    public String getMealItems(String mealKey) {
        if ("breakfast".equalsIgnoreCase(mealKey)) return breakfast;
        if ("lunch".equalsIgnoreCase(mealKey)) return lunch;
        if ("snacks".equalsIgnoreCase(mealKey)) return snacks;
        if ("dinner".equalsIgnoreCase(mealKey)) return dinner;
        return "";
    }

    @NonNull
    @Override
    public String toString() {
        return (week != null ? week.toUpperCase() : "") + " " + day + " [Breakfast=" + breakfast + ", Lunch=" + lunch + "]";
    }
}
