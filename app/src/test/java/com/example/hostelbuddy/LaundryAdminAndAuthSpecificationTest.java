package com.example.hostelbuddy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verification test suite for NMIMS Campus Management App - Laundry Admin & Authentication specification.
 */
public class LaundryAdminAndAuthSpecificationTest {

    @Test
    public void testRoleNormalization() {
        assertEquals("student", LoginActivity.normalizeRole("student"));
        assertEquals("student", LoginActivity.normalizeRole("Student"));

        assertEquals("laundry_admin", LoginActivity.normalizeRole("laundry_admin"));
        assertEquals("laundry_admin", LoginActivity.normalizeRole("Laundry Admin"));
        assertEquals("laundry_admin", LoginActivity.normalizeRole("laundry-admin"));

        assertEquals("mess_admin", LoginActivity.normalizeRole("mess_admin"));
        assertEquals("mess_admin", LoginActivity.normalizeRole("Mess & Campus Cravings Admin"));

        assertEquals("food_truck_admin", LoginActivity.normalizeRole("food_truck_admin"));
        assertEquals("food_truck_admin", LoginActivity.normalizeRole("cc_admin"));
        assertEquals("food_truck_admin", LoginActivity.normalizeRole("CC Admin"));
        assertEquals("food_truck_admin", LoginActivity.normalizeRole("campus_cravings_admin"));

        assertEquals("menu_admin", LoginActivity.normalizeRole("menu_admin"));
        assertEquals("super_admin", LoginActivity.normalizeRole("super_admin"));
    }

    @Test
    public void testSection32LaundryOrderSchema() {
        LaundryOrder order = new LaundryOrder();
        order.setOrderId("HB-8821");
        order.setStudentUid("student_firebase_uid_123");
        order.setSapId("0070022400269"); // Leading zeros preserved
        order.setStudentName("Ritam Sharma");
        order.setUserEmail("ritam.sharma@nmims.edu");
        order.setUserRoom("Room 504");

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "cloth_wash_iron");
        item1.put("name", "Cloth (Wash + Iron)");
        item1.put("quantity", 4);
        item1.put("unitPrice", 15.0);
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "bedsheet");
        item2.put("name", "Bed Sheet (Single)");
        item2.put("quantity", 2);
        item2.put("unitPrice", 25.0);
        items.add(item2);

        order.setItems(items);
        order.setEstimatedPrice(110.0);
        order.setFinalPrice(110.0);
        order.setStatus(LaundryOrder.STATUS_RECEIVED);
        order.setRemarks("Delicate wash for bedsheets");
        order.setCreatedBy("admin_firebase_uid_999");

        assertEquals(6, order.getTotalItemCount());
        assertTrue(order.getItemsSummary().contains("Cloth (Wash + Iron) × 4"));
        assertTrue(order.getItemsSummary().contains("Bed Sheet (Single) × 2"));
        assertTrue(order.isConfirmed());
        assertEquals(110.0, order.getEffectivePrice(), 0.001);

        // Serialize to Firestore map
        Map<String, Object> map = order.toMap();
        assertEquals("HB-8821", map.get("orderId"));
        assertEquals("student_firebase_uid_123", map.get("studentUid"));
        assertEquals("0070022400269", map.get("sapId"));
        assertEquals("Ritam Sharma", map.get("studentName"));
        assertEquals(110.0, (Double) map.get("estimatedPrice"), 0.001);
        assertEquals(110.0, (Double) map.get("finalPrice"), 0.001);
        assertEquals("Delicate wash for bedsheets", map.get("remarks"));
        assertEquals("admin_firebase_uid_999", map.get("createdBy"));

        // Deserialization from snapshot
        LaundryOrder deserialized = LaundryOrder.fromSnapshot("HB-8821", map);
        assertEquals("HB-8821", deserialized.getOrderId());
        assertEquals("student_firebase_uid_123", deserialized.getStudentUid());
        assertEquals("0070022400269", deserialized.getSapId());
        assertEquals("Ritam Sharma", deserialized.getStudentName());
        assertEquals(6, deserialized.getTotalItemCount());
        assertEquals(110.0, deserialized.getFinalPrice(), 0.001);
    }

    @Test
    public void testSection24SupportedStatuses() {
        LaundryOrder order = new LaundryOrder();

        order.setStatus(LaundryOrder.STATUS_REGISTERED);
        assertEquals(LaundryOrder.STATUS_REGISTERED, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_RECEIVED);
        assertEquals(LaundryOrder.STATUS_RECEIVED, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_WASHING);
        assertEquals(LaundryOrder.STATUS_WASHING, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_DRYING);
        assertEquals(LaundryOrder.STATUS_DRYING, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_IRONING);
        assertEquals(LaundryOrder.STATUS_IRONING, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_READY);
        assertEquals(LaundryOrder.STATUS_READY, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_COLLECTED);
        assertEquals(LaundryOrder.STATUS_COLLECTED, order.getStatus());

        order.setStatus(LaundryOrder.STATUS_CANCELLED);
        assertEquals(LaundryOrder.STATUS_CANCELLED, order.getStatus());
    }

    @Test
    public void testDashboardSectionCategorization() {
        // Active orders: Registered, Received, Washing, Drying, Ironing
        LaundryOrder active1 = new LaundryOrder();
        active1.setStatus(LaundryOrder.STATUS_REGISTERED);
        assertTrue(active1.isActive());

        LaundryOrder active2 = new LaundryOrder();
        active2.setStatus(LaundryOrder.STATUS_WASHING);
        assertTrue(active2.isActive());

        // Ready for collection is also active until collected
        LaundryOrder ready = new LaundryOrder();
        ready.setStatus(LaundryOrder.STATUS_READY);
        assertTrue(ready.isActive());

        // Completed / Collected orders are not active
        LaundryOrder collected = new LaundryOrder();
        collected.setStatus(LaundryOrder.STATUS_COLLECTED);
        assertFalse(collected.isActive());

        LaundryOrder cancelled = new LaundryOrder();
        cancelled.setStatus(LaundryOrder.STATUS_CANCELLED);
        assertFalse(cancelled.isActive());
    }
}
