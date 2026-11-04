package com.example.hostelbuddy;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class MessMenuParserTest {

    @Test
    public void testValidCsvParsing() {
        String csv = "week,day,breakfast,lunch,snacks,dinner,venue,specialDinner\n" +
                "1,Monday,\"Aloo Paratha, Curd, Tea\",\"Chana Masala, Rice\",\"Tea, Fruits\",\"Dal Makhani, Chapati\",Main Mess,\n" +
                "1,Tuesday,\"Idli, Sambar\",\"Mix Veg, Rice\",\"Maggi\",\"Paneer, Chapati\",Main Mess,\n" +
                "2,Monday,\"Poha, Tea\",\"Rajma, Rice\",\"Sandwich\",\"Veg Pulao, Raita\",Main Mess,Special Gulab Jamun\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        MessMenuParser.ParseResult result = MessMenuParser.parseCsv(in);

        assertTrue("Expected valid parse result", result.isValid());
        assertEquals(3, result.getMenuDays().size());

        MessMenuDay day1 = result.getMenuDays().get(0);
        assertEquals("week1", day1.getWeek());
        assertEquals("Monday", day1.getDay());
        assertEquals("monday", day1.getDayKey());
        assertEquals("Aloo Paratha, Curd, Tea", day1.getBreakfast());
        assertEquals("Chana Masala, Rice", day1.getLunch());
        assertEquals("Main Mess", day1.getVenue());

        MessMenuDay day3 = result.getMenuDays().get(2);
        assertEquals("week2", day3.getWeek());
        assertEquals("Special Gulab Jamun", day3.getSpecialDinner());
        assertEquals("week2_monday", day3.getDocumentId());
    }

    @Test
    public void testMalformedCsvMissingRequiredHeaders() {
        String csv = "breakfast,lunch,dinner\n" +
                "Idli,Rice,Roti\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        MessMenuParser.ParseResult result = MessMenuParser.parseCsv(in);

        assertFalse("Expected invalid parse result for missing headers", result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Missing required column 'week'")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Missing required column 'day'")));
    }

    @Test
    public void testInvalidWeekAndDayDetection() {
        String csv = "week,day,breakfast\n" +
                "3,Funday,Poha\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        MessMenuParser.ParseResult result = MessMenuParser.parseCsv(in);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid 'week' value")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid 'day' value")));
    }

    @Test
    public void testDuplicateRowDetection() {
        String csv = "week,day,breakfast\n" +
                "1,Monday,Poha\n" +
                "1,Monday,Idli\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        MessMenuParser.ParseResult result = MessMenuParser.parseCsv(in);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate menu record for WEEK1 Monday")));
    }

    @Test
    public void testQuotedFieldWithEscapedQuotes() {
        String line = "1,Monday,\"\"\"Special Paneer\"\", Naan\",Rice";
        List<String> cells = MessMenuParser.parseCsvLine(line);

        assertEquals(4, cells.size());
        assertEquals("1", cells.get(0));
        assertEquals("Monday", cells.get(1));
        assertEquals("\"Special Paneer\", Naan", cells.get(2));
        assertEquals("Rice", cells.get(3));
    }
}
