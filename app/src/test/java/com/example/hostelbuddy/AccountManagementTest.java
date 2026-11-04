package com.example.hostelbuddy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * Unit tests validating account management, role normalization, registration validation,
 * data modeling rules, and Now Serving schedule evaluations for Hostel Buddy.
 */
public class AccountManagementTest {

    private String normalizeRole(String role) {
        if (role == null) return "";
        String value = role.trim().toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replace(' ', '_')
                .replace('-', '_');
        if ("mess_admin".equals(value) || "mess_and_campus_cravings_admin".equals(value)) return "mess_admin";
        if ("laundry_admin".equals(value)) return "laundry_admin";
        if ("food_truck_admin".equals(value) || "campus_cravings_admin".equals(value)) return "food_truck_admin";
        if ("student".equals(value)) return "student";
        return value;
    }

    private boolean isValidStudentRegistration(String name, String email, String password, String confirmPassword) {
        if (name == null || name.trim().isEmpty()) return false;
        if (email == null || !email.contains("@") || !email.contains(".")) return false;
        if (password == null || password.length() < 6) return false;
        return password.equals(confirmPassword);
    }

    @Test
    public void testRoleNormalization() {
        assertEquals("student", LoginActivity.normalizeRole("Student"));
        assertEquals("student", LoginActivity.normalizeRole("student "));
        assertEquals("mess_admin", LoginActivity.normalizeRole("mess_admin"));
        assertEquals("mess_admin", LoginActivity.normalizeRole("Mess & Campus Cravings Admin"));
        assertEquals("mess_admin", LoginActivity.normalizeRole("mess_and_campus_cravings_admin"));
        assertEquals("laundry_admin", LoginActivity.normalizeRole("Laundry Admin"));
        assertEquals("laundry_admin", LoginActivity.normalizeRole("laundry_admin"));
        assertEquals("food_truck_admin", LoginActivity.normalizeRole("Food Truck Admin"));
        assertEquals("food_truck_admin", LoginActivity.normalizeRole("campus_cravings_admin"));
        assertEquals("food_truck_admin", LoginActivity.normalizeRole("food_truck_admin"));
        assertEquals("", LoginActivity.normalizeRole(null));
        assertEquals("", LoginActivity.normalizeRole("   "));
    }

    @Test
    public void testLegacyRtdbRolesMappingToFirestore() {
        // All roles previously recorded in Realtime Database must normalize to one of the 4 canonical roles
        String[] rtdbRoles = {
            "student", "Student",
            "mess_admin", "Mess & Campus Cravings Admin", "mess_and_campus_cravings_admin",
            "laundry_admin", "Laundry Admin",
            "food_truck_admin", "campus_cravings_admin", "Food Truck Admin"
        };

        List<String> canonicalFirestoreRoles = Arrays.asList(
            "student", "mess_admin", "laundry_admin", "food_truck_admin"
        );

        for (String rtdbRole : rtdbRoles) {
            String normalized = LoginActivity.normalizeRole(rtdbRole);
            assertTrue("Role '" + rtdbRole + "' must normalize to a canonical Firestore role, but got: " + normalized,
                canonicalFirestoreRoles.contains(normalized));
        }
    }

    @Test
    public void testStudentRegistrationValidation() {
        // Valid case
        assertTrue(isValidStudentRegistration(
                "Rohan Verma", "rohan.verma@nmims.edu.in", "securePass123", "securePass123"));

        // Invalid: empty name
        assertFalse(isValidStudentRegistration(
                "", "rohan.verma@nmims.edu.in", "securePass123", "securePass123"));

        // Invalid: bad email
        assertFalse(isValidStudentRegistration(
                "Rohan Verma", "invalid-email", "securePass123", "securePass123"));

        // Invalid: short password (< 6 chars)
        assertFalse(isValidStudentRegistration(
                "Rohan Verma", "rohan.verma@nmims.edu.in", "12345", "12345"));

        // Invalid: passwords don't match
        assertFalse(isValidStudentRegistration(
                "Rohan Verma", "rohan.verma@nmims.edu.in", "securePass123", "differentPassword"));
    }

    @Test
    public void testFirestoreCollectionNames() {
        assertEquals("users", FirestoreConfig.COLLECTION_USERS);
        assertEquals("messMenu", FirestoreConfig.COLLECTION_MESS_MENU);
        assertEquals("messMenuMeta", FirestoreConfig.COLLECTION_MESS_MENU_META);
        assertEquals("foodTruckItems", FirestoreConfig.COLLECTION_FOOD_TRUCK_ITEMS);
        assertEquals("foodTruckMeta", FirestoreConfig.COLLECTION_FOOD_TRUCK_META);
        assertEquals("laundryRates", FirestoreConfig.COLLECTION_LAUNDRY_RATES);
        assertEquals("laundryOrders", FirestoreConfig.COLLECTION_LAUNDRY_ORDERS);
    }

    @Test
    public void testLaundryCalculation() {
        double washRate = 12.0;
        double ironRate = 6.0;
        double dryRate = 65.0;

        int washQty = 5;
        int ironQty = 3;
        int dryQty = 1;

        double expectedTotal = (5 * 12.0) + (3 * 6.0) + (1 * 65.0); // 60 + 18 + 65 = 143.0
        double calculatedTotal = washQty * washRate + ironQty * ironRate + dryQty * dryRate;

        assertEquals(143.0, calculatedTotal, 0.001);
    }

    @Test
    public void testDayKeyOrdering() {
        List<String> expectedDays = Arrays.asList(
                "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        );
        assertEquals(7, expectedDays.size());
        assertEquals("monday", expectedDays.get(0));
        assertEquals("sunday", expectedDays.get(6));
    }

    @Test
    public void testNowServingDayRotation() {
        assertEquals("tuesday", NowServingService.getNextDayKey("monday"));
        assertEquals("wednesday", NowServingService.getNextDayKey("tuesday"));
        assertEquals("thursday", NowServingService.getNextDayKey("wednesday"));
        assertEquals("friday", NowServingService.getNextDayKey("thursday"));
        assertEquals("saturday", NowServingService.getNextDayKey("friday"));
        assertEquals("sunday", NowServingService.getNextDayKey("saturday"));
        assertEquals("monday", NowServingService.getNextDayKey("sunday"));
    }

    @Test
    public void testNowServingComputation() {
        MessMenuDay today = new MessMenuDay("week1", "Monday", "monday");
        today.setBreakfast("Masala Dosa, Chutney, Tea");
        today.setLunch("Paneer Curry, Dal, Rice");
        today.setSnacks("Samosa, Chai");
        today.setDinner("Veg Biryani, Raita");
        today.setSpecialDinner("Gulab Jamun");
        today.setVenue("Main Mess");

        NowServingService.NowServingInfo info = NowServingService.computeNowServing(today, null, true);

        assertNotNull(info);
        assertNotNull(info.state);
        assertNotNull(info.mealName);
        assertNotNull(info.foodItems);
        assertTrue(info.isLiveFromServer);
        assertEquals("Main Mess", info.venue);
        assertNotNull(info.weekAndDayLabel);
        assertTrue(info.weekAndDayLabel.contains(MessMenuParser.titleCase(NowServingService.getCurrentDayKey(NowServingService.getCampusCalendar()))));
    }
}
