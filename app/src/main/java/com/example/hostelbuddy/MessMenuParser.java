package com.example.hostelbuddy;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Robust CSV parser and validator for the two-week rotating hostel mess menu.
 * Adheres strictly to RFC 4180 (quoted fields, commas within quotes, UTF-8 BOM, empty row handling).
 */
public final class MessMenuParser {

    private static final String[] VALID_DAYS = {
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    };

    public static class ParseResult {
        private final List<MessMenuDay> menuDays = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public boolean isValid() {
            return errors.isEmpty() && !menuDays.isEmpty();
        }

        public List<MessMenuDay> getMenuDays() {
            return menuDays;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public void addDay(MessMenuDay day) {
            menuDays.add(day);
        }
    }

    private MessMenuParser() { }

    @NonNull
    public static ParseResult parseCsv(@NonNull InputStream inputStream) {
        ParseResult result = new ParseResult();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.addError("The uploaded CSV file is empty.");
                return result;
            }

            // Strip UTF-8 BOM if present
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> columnMap = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                columnMap.put(normalizeHeader(headers.get(i)), i);
            }

            if (!columnMap.containsKey("week")) {
                result.addError("Missing required column 'week' in CSV header.");
            }
            if (!columnMap.containsKey("day")) {
                result.addError("Missing required column 'day' in CSV header.");
            }

            if (!result.getErrors().isEmpty()) {
                return result;
            }

            Set<String> seenWeekDays = new HashSet<>();
            String line;
            int lineNumber = 1; // 1 was header

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty rows safely
                }

                List<String> cells = parseCsvLine(line);
                String rawWeek = getValue(cells, columnMap, "week");
                String rawDay = getValue(cells, columnMap, "day");

                boolean rowHasError = false;
                String normalizedWeek = normalizeWeek(rawWeek);
                if (normalizedWeek == null) {
                    result.addError("Row " + lineNumber + ": Invalid 'week' value (\"" + rawWeek + "\"). Expected 1 or 2 (or 'week1' / 'week2').");
                    rowHasError = true;
                }

                String normalizedDay = normalizeDay(rawDay);
                if (normalizedDay == null) {
                    result.addError("Row " + lineNumber + ": Invalid 'day' value (\"" + rawDay + "\"). Expected day name (Monday - Sunday).");
                    rowHasError = true;
                }

                if (rowHasError) {
                    continue;
                }

                String uniqueKey = normalizedWeek + "_" + normalizedDay;
                if (seenWeekDays.contains(uniqueKey)) {
                    result.addError("Row " + lineNumber + ": Duplicate menu record for " + normalizedWeek.toUpperCase(Locale.ROOT) + " " + titleCase(normalizedDay) + ".");
                    continue;
                }
                seenWeekDays.add(uniqueKey);

                MessMenuDay menuDay = new MessMenuDay(normalizedWeek, titleCase(normalizedDay), normalizedDay);
                menuDay.setBreakfast(getValue(cells, columnMap, "breakfast"));
                menuDay.setLunch(getValue(cells, columnMap, "lunch"));
                menuDay.setSnacks(getValue(cells, columnMap, "snacks"));
                menuDay.setDinner(getValue(cells, columnMap, "dinner"));
                menuDay.setVenue(getValue(cells, columnMap, "venue"));
                menuDay.setSpecialDinner(getValue(cells, columnMap, "specialdinner"));
                menuDay.setSpecialDinnerVenue(getValue(cells, columnMap, "specialdinnervenue"));

                // Optional serving hours from CSV
                menuDay.setBreakfastHours(getValue(cells, columnMap, "breakfasthours"));
                menuDay.setLunchHours(getValue(cells, columnMap, "lunchhours"));
                menuDay.setSnacksHours(getValue(cells, columnMap, "snackshours"));
                menuDay.setDinnerHours(getValue(cells, columnMap, "dinnerhours"));

                // Verify that at least one meal has content
                boolean hasContent = (menuDay.getBreakfast() != null && !menuDay.getBreakfast().isEmpty())
                        || (menuDay.getLunch() != null && !menuDay.getLunch().isEmpty())
                        || (menuDay.getSnacks() != null && !menuDay.getSnacks().isEmpty())
                        || (menuDay.getDinner() != null && !menuDay.getDinner().isEmpty())
                        || (menuDay.getSpecialDinner() != null && !menuDay.getSpecialDinner().isEmpty());

                if (!hasContent) {
                    result.addWarning("Row " + lineNumber + " (" + menuDay.getDay() + "): No meals specified (empty row).");
                }

                result.addDay(menuDay);
            }

            if (result.getMenuDays().isEmpty() && result.getErrors().isEmpty()) {
                result.addError("No valid menu records were found in the CSV.");
            }

        } catch (Exception e) {
            result.addError("CSV parsing failed: " + e.getMessage());
        }

        return result;
    }

    public static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        if (line == null) return cells;

        StringBuilder currentCell = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentCell.append('"');
                    i++; // skip escaped quote
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (c == ',' && !insideQuotes) {
                cells.add(currentCell.toString().trim());
                currentCell.setLength(0);
            } else {
                currentCell.append(c);
            }
        }
        cells.add(currentCell.toString().trim());
        return cells;
    }

    private static String getValue(List<String> cells, Map<String, Integer> columnMap, String colName) {
        Integer index = columnMap.get(normalizeHeader(colName));
        if (index == null || index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    public static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    public static String normalizeWeek(String week) {
        if (week == null) return null;
        String clean = normalizeHeader(week);
        if ("1".equals(clean) || "week1".equals(clean) || "w1".equals(clean)) return "week1";
        if ("2".equals(clean) || "week2".equals(clean) || "w2".equals(clean)) return "week2";
        return null;
    }

    public static String normalizeDay(String day) {
        if (day == null) return null;
        String clean = normalizeHeader(day);
        for (String valid : VALID_DAYS) {
            if (valid.equals(clean) || (clean.length() >= 3 && valid.startsWith(clean.substring(0, 3)))) {
                return valid;
            }
        }
        return null;
    }

    public static String titleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
