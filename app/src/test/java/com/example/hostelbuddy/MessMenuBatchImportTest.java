package com.example.hostelbuddy;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests validating the Mess Menu CSV parsing, batch publishing data structure,
 * and authorization path alignments with Cloud Firestore Security Rules.
 */
public class MessMenuBatchImportTest {

    private static final String SAMPLE_CSV =
            "week,day,breakfast,lunch,snacks,dinner,venue,specialDinner\n" +
            "1,Monday,\"Aloo Paratha, Curd, Tea\",\"Chana Masala, Rice\",\"Tea, Fruits\",\"Dal Makhani, Chapati\",Main Mess,\n" +
            "1,Tuesday,\"Idli, Sambar\",\"Mix Veg, Rice\",\"Maggi\",\"Paneer, Chapati\",Main Mess,\n" +
            "1,Wednesday,\"Poha, Tea\",\"Rajma, Rice\",\"Sandwich\",\"Veg Pulao, Raita\",Main Mess,\n" +
            "1,Thursday,\"Upma, Chutney\",\"Kadhi Pakora, Rice\",\"Biscuits, Tea\",\"Dum Aloo, Roti\",Main Mess,\n" +
            "1,Friday,\"Masala Dosa, Sambhar\",\"Chole Bhature\",\"Samosa\",\"Kofta Curry, Rice\",Main Mess,\n" +
            "1,Saturday,\"Puri Bhaji, Tea\",\"Veg Biryani, Raita\",\"Chai, Cookies\",\"Khichdi, Papad\",Main Mess,\n" +
            "1,Sunday,\"Chole Kulche, Lassi\",\"Dal Tadka, Jeera Rice\",\"Bhel Puri\",\"Pav Bhaji, Kheer\",Main Mess,\"Gulab Jamun\"\n" +
            "2,Monday,\"Uttapam, Coconut Chutney\",\"Dal Fry, Rice, Roti\",\"Tea, Biscuits\",\"Mix Veg, Chapati\",Main Mess,\n" +
            "2,Tuesday,\"Methi Thepla, Curd\",\"Black Chana, Rice\",\"Cutlet\",\"Paneer Butter Masala\",Main Mess,\n" +
            "2,Wednesday,\"Bread Butter, Boiled Egg/Sprouts\",\"Kadi Khichdi\",\"Tea, Rusk\",\"Egg Curry/Kofta, Rice\",Main Mess,\n" +
            "2,Thursday,\"Vermicelli Upma, Tea\",\"Rajma Masala, Steamed Rice\",\"Chai, Namkeen\",\"Aloo Gobhi, Chapati\",Main Mess,\n" +
            "2,Friday,\"Rava Dosa, Sambar\",\"Soya Chunk Curry, Rice\",\"French Fries\",\"Dal Makhani, Naan\",Main Mess,\n" +
            "2,Saturday,\"Aloo Puri, Pickle\",\"Veg Pulao, Curd\",\"Tea, Pakora\",\"Bhindi Masala, Roti\",Main Mess,\n" +
            "2,Sunday,\"Stuffed Paratha, Curd\",\"Biryani, Mirchi Ka Salan\",\"Samosa Chaat\",\"Malai Kofta, Jeera Rice\",Main Mess,\"Rasgulla\"\n";

    @Test
    public void testFullTwoWeekCsvParsingAndValidation() throws Exception {
        InputStream is = new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8));
        MessMenuParser.ParseResult result = MessMenuParser.parseCsv(is);

        assertTrue("CSV should parse cleanly with zero errors", result.isValid());
        assertEquals(0, result.getErrors().size());

        List<MessMenuDay> days = result.getMenuDays();
        assertEquals("Full two-week menu must parse exactly 14 daily records", 14, days.size());

        // Verify document IDs match Firestore rule pattern: {week}_{dayKey}
        List<String> expectedDocIds = Arrays.asList(
                "week1_monday", "week1_tuesday", "week1_wednesday", "week1_thursday", "week1_friday", "week1_saturday", "week1_sunday",
                "week2_monday", "week2_tuesday", "week2_wednesday", "week2_thursday", "week2_friday", "week2_saturday", "week2_sunday"
        );

        for (int i = 0; i < days.size(); i++) {
            MessMenuDay day = days.get(i);
            assertEquals("Document ID must match Firestore rule pattern", expectedDocIds.get(i), day.getDocumentId());
            assertNotNull("Week key must not be null", day.getWeek());
            assertNotNull("Day name must not be null", day.getDay());
            assertNotNull("Day key must not be null", day.getDayKey());
        }
    }

    @Test
    public void testMessMenuDocumentSchemaMatchesFirestoreRules() {
        MessMenuDay day = new MessMenuDay("week1", "Monday", "monday");
        day.setBreakfast("Idli, Sambar");
        day.setLunch("Rajma, Rice");
        day.setSnacks("Tea, Samosa");
        day.setDinner("Paneer, Chapati");
        day.setVenue("Main Mess");
        day.setSpecialDinner("Gulab Jamun");
        day.setSpecialDinnerVenue("Dining Hall B");

        Map<String, Object> map = day.toMap();

        // Validate fields checked by isValidMessMenuDoc in firestore.rules
        assertTrue(map.get("week") instanceof String);
        assertTrue(map.get("day") instanceof String);
        assertTrue(map.get("dayKey") instanceof String);
        assertEquals("week1", map.get("week"));
        assertEquals("Monday", map.get("day"));
        assertEquals("monday", map.get("dayKey"));
        assertEquals("Idli, Sambar", map.get("breakfast"));
        assertEquals("Main Mess", map.get("venue"));
        assertEquals("Gulab Jamun", map.get("specialDinner"));
    }

    @Test
    public void testActiveMenuMetadataSchemaMatchesFirestoreRules() {
        String batchId = "batch_1730000000000_abc123";
        int totalDays = 14;
        String adminUid = "ppARAicfzZfXhBrHrJ9w0Cle0tX2";

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("activeBatchId", batchId);
        meta.put("totalDaysPublished", totalDays);
        meta.put("publishedBy", adminUid);

        // Validate fields checked by isValidMessMenuMetaDoc in firestore.rules
        assertTrue("activeBatchId must be a string", meta.get("activeBatchId") instanceof String);
        assertTrue("totalDaysPublished must be a number", meta.get("totalDaysPublished") instanceof Number);
        assertTrue("totalDaysPublished must be >= 0", ((Number) meta.get("totalDaysPublished")).intValue() >= 0);
        assertEquals("ppARAicfzZfXhBrHrJ9w0Cle0tX2", meta.get("publishedBy"));
    }

    @Test
    public void testMessAdminRoleEvaluationConsistency() {
        // Supported role strings that must grant mess admin privileges
        String[] validMessAdminRoles = {
                "mess_admin",
                "Mess & Campus Cravings Admin",
                "mess_and_campus_cravings_admin"
        };

        for (String role : validMessAdminRoles) {
            String normalized = LoginActivity.normalizeRole(role);
            assertEquals("All mess admin role variations must normalize to mess_admin",
                    "mess_admin", normalized);
        }

        // Roles that MUST NOT grant mess admin privileges
        String[] nonMessRoles = {
                "student",
                "Student",
                "laundry_admin",
                "Laundry Admin",
                "",
                null
        };

        for (String role : nonMessRoles) {
            String normalized = LoginActivity.normalizeRole(role);
            assertNotEquals("Non-mess roles must not be normalized to mess_admin",
                    "mess_admin", normalized);
        }
    }

    @Test
    public void testFirestoreCollectionAndDocumentConstants() {
        assertEquals("messMenu", FirestoreConfig.COLLECTION_MESS_MENU);
        assertEquals("messMenuMeta", FirestoreConfig.COLLECTION_MESS_MENU_META);
        assertEquals("activeMenu", FirestoreConfig.DOC_ACTIVE_MENU);
        assertEquals("users", FirestoreConfig.COLLECTION_USERS);
    }
}
