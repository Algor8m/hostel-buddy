package com.example.hostelbuddy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression test suite verifying:
 * 1. Safe handling of invalid array indices (-1, out of bounds) in filter and status resolution.
 * 2. Safe handling of unmatched/unknown order statuses and section tabs.
 * 3. Graceful handling of null/empty lists and missing fields in LaundryOrder filtering.
 */
public class LaundryAdminFilterRegressionTest {

    @Test
    public void testResolveFilterOptionWithInvalidIndexMinusOne() {
        // Crash scenario: statusFilterSpinner.getSelectedItemPosition() returns -1 before selection
        String result = LaundryAdminActivity.resolveFilterOption(-1, LaundryAdminActivity.FILTER_OPTIONS);
        assertEquals("All Statuses", result);
    }

    @Test
    public void testResolveFilterOptionWithOutOfBoundsIndices() {
        // Test array boundary values and null/empty arrays
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(9, LaundryAdminActivity.FILTER_OPTIONS));
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(100, LaundryAdminActivity.FILTER_OPTIONS));
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(Integer.MIN_VALUE, LaundryAdminActivity.FILTER_OPTIONS));
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(Integer.MAX_VALUE, LaundryAdminActivity.FILTER_OPTIONS));
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(-1, null));
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(-1, new String[0]));

        // Test valid indices
        assertEquals("All Statuses", LaundryAdminActivity.resolveFilterOption(0, LaundryAdminActivity.FILTER_OPTIONS));
        assertEquals(LaundryOrder.STATUS_REGISTERED, LaundryAdminActivity.resolveFilterOption(1, LaundryAdminActivity.FILTER_OPTIONS));
        assertEquals(LaundryOrder.STATUS_RECEIVED, LaundryAdminActivity.resolveFilterOption(2, LaundryAdminActivity.FILTER_OPTIONS));
    }

    @Test
    public void testResolveSelectedStatusWithInvalidIndices() {
        String[] supported = LaundryAdminActivity.SUPPORTED_STATUSES;
        String fallback = "FallbackStatus";

        // -1 position must return fallback
        assertEquals(fallback, LaundryAdminActivity.resolveSelectedStatus(-1, supported, fallback));
        assertEquals(fallback, LaundryAdminActivity.resolveSelectedStatus(99, supported, fallback));
        assertEquals(fallback, LaundryAdminActivity.resolveSelectedStatus(-5, supported, fallback));
        assertEquals(fallback, LaundryAdminActivity.resolveSelectedStatus(-1, null, fallback));

        // Valid position returns matching status
        assertEquals(supported[0], LaundryAdminActivity.resolveSelectedStatus(0, supported, fallback));
    }

    @Test
    public void testResolveNextStatusIndexWithUnknownOrNullStatus() {
        String[] supported = LaundryAdminActivity.SUPPORTED_STATUSES;

        // Unknown, null, or whitespace status must return 0 without throwing
        assertEquals(0, LaundryAdminActivity.resolveNextStatusIndex(null, supported));
        assertEquals(0, LaundryAdminActivity.resolveNextStatusIndex("", supported));
        assertEquals(0, LaundryAdminActivity.resolveNextStatusIndex("UNKNOWN_STATUS_xyz", supported));
        assertEquals(0, LaundryAdminActivity.resolveNextStatusIndex("Unregistered", supported));

        // Valid status advances to next index
        int registeredIndex = 0; // STATUS_REGISTERED is at index 0
        int nextIndex = LaundryAdminActivity.resolveNextStatusIndex(LaundryOrder.STATUS_REGISTERED, supported);
        assertEquals(registeredIndex + 1, nextIndex);
    }

    @Test
    public void testFilterOrdersWithEmptyAndNullLists() {
        // Must safely return empty list without crashing
        List<LaundryOrder> nullResult = LaundryAdminActivity.filterOrders(null, LaundryAdminActivity.SECTION_ACTIVE, "All Statuses", "");
        assertNotNull(nullResult);
        assertTrue(nullResult.isEmpty());

        List<LaundryOrder> emptyResult = LaundryAdminActivity.filterOrders(new ArrayList<>(), LaundryAdminActivity.SECTION_ACTIVE, "All Statuses", "");
        assertNotNull(emptyResult);
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    public void testFilterOrdersWithUnknownSectionAndUnknownStatus() {
        List<LaundryOrder> orders = new ArrayList<>();

        LaundryOrder normalOrder = new LaundryOrder();
        normalOrder.setOrderId("HB-1001");
        normalOrder.setStatus(LaundryOrder.STATUS_RECEIVED);
        orders.add(normalOrder);

        LaundryOrder weirdStatusOrder = new LaundryOrder();
        weirdStatusOrder.setOrderId("HB-1002");
        weirdStatusOrder.setStatus("MYSTERY_STATUS_99");
        orders.add(weirdStatusOrder);

        LaundryOrder nullStatusOrder = new LaundryOrder();
        nullStatusOrder.setOrderId("HB-1003");
        nullStatusOrder.setStatus(null);
        orders.add(nullStatusOrder);

        // Unknown section must not crash and should safely evaluate
        List<LaundryOrder> filtered = LaundryAdminActivity.filterOrders(orders, "COMPLETELY_UNKNOWN_SECTION", "All Statuses", "");
        assertNotNull(filtered);
        // By default, unknown section falls back to active, which includes orders not marked ready/completed/cancelled
        assertEquals(3, filtered.size());
    }

    @Test
    public void testFilterOrdersSectionSeparation() {
        List<LaundryOrder> orders = new ArrayList<>();

        LaundryOrder activeOrder = new LaundryOrder();
        activeOrder.setOrderId("HB-2001");
        activeOrder.setStatus(LaundryOrder.STATUS_WASHING);
        orders.add(activeOrder);

        LaundryOrder readyOrder = new LaundryOrder();
        readyOrder.setOrderId("HB-2002");
        readyOrder.setStatus(LaundryOrder.STATUS_READY);
        orders.add(readyOrder);

        LaundryOrder completedOrder = new LaundryOrder();
        completedOrder.setOrderId("HB-2003");
        completedOrder.setStatus(LaundryOrder.STATUS_COLLECTED);
        orders.add(completedOrder);

        // SECTION_ACTIVE should only include activeOrder
        List<LaundryOrder> activeList = LaundryAdminActivity.filterOrders(orders, LaundryAdminActivity.SECTION_ACTIVE, "All Statuses", "");
        assertEquals(1, activeList.size());
        assertEquals("HB-2001", activeList.get(0).getOrderId());

        // SECTION_READY should only include readyOrder
        List<LaundryOrder> readyList = LaundryAdminActivity.filterOrders(orders, LaundryAdminActivity.SECTION_READY, "All Statuses", "");
        assertEquals(1, readyList.size());
        assertEquals("HB-2002", readyList.get(0).getOrderId());

        // SECTION_COMPLETED should only include completedOrder
        List<LaundryOrder> completedList = LaundryAdminActivity.filterOrders(orders, LaundryAdminActivity.SECTION_COMPLETED, "All Statuses", "");
        assertEquals(1, completedList.size());
        assertEquals("HB-2003", completedList.get(0).getOrderId());

        // SECTION_ALL should include all 3 orders
        List<LaundryOrder> allList = LaundryAdminActivity.filterOrders(orders, LaundryAdminActivity.SECTION_ALL, "All Statuses", "");
        assertEquals(3, allList.size());
    }

    @Test
    public void testSearchQueryWithNullAndMissingFields() {
        LaundryOrder sparseOrder = new LaundryOrder();
        sparseOrder.setOrderId("HB-9999");
        sparseOrder.setStudentName(null);
        sparseOrder.setSapId(null);
        sparseOrder.setUserEmail(null);
        sparseOrder.setUserRoom(null);
        sparseOrder.setStatus(LaundryOrder.STATUS_REGISTERED);

        List<LaundryOrder> orders = new ArrayList<>();
        orders.add(sparseOrder);

        // Searching by orderId works
        List<LaundryOrder> matched = LaundryAdminActivity.filterOrders(orders, LaundryAdminActivity.SECTION_ALL, "All Statuses", "9999");
        assertEquals(1, matched.size());

        // Searching for absent field doesn't match and doesn't crash
        List<LaundryOrder> unmatched = LaundryAdminActivity.filterOrders(orders, LaundryAdminActivity.SECTION_ALL, "All Statuses", "NonExistentName");
        assertTrue(unmatched.isEmpty());
    }
}
