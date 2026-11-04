package com.example.hostelbuddy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class LaundryOrderTest {

    @Test
    public void testOrderSerializationAndLifecycle() {
        LaundryOrder order = new LaundryOrder();
        order.setOrderId("ORD-20261024-12345");
        order.setUserId("student_uid_999");
        order.setUserEmail("student@nmims.edu.in");
        order.setUserName("Amit Kumar");
        order.setUserRoom("Room 204");
        order.setWashCount(3);
        order.setIronCount(2);
        order.setDryCount(1);
        order.setBedsheetCount(1);
        order.setEstimatedTotal(125.0);
        order.setStatus(LaundryOrder.STATUS_RECEIVED);

        assertTrue("New order in Received status should allow student cancellation", order.canStudentCancel());

        Map<String, Object> map = order.toMap();
        assertEquals("ORD-20261024-12345", map.get("orderId"));
        assertEquals("student_uid_999", map.get("userId"));
        assertEquals(3, map.get("washCount"));
        assertEquals(125.0, (Double) map.get("estimatedTotal"), 0.001);
        assertEquals(LaundryOrder.STATUS_RECEIVED, map.get("status"));

        // Simulate status advance by admin
        order.setStatus(LaundryOrder.STATUS_IN_PROGRESS);
        assertFalse("In progress order must NOT allow student cancellation", order.canStudentCancel());

        order.setStatus(LaundryOrder.STATUS_COMPLETED);
        assertFalse("Completed order must NOT allow cancellation", order.canStudentCancel());
    }

    @Test
    public void testFromSnapshotDeserialization() {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", "uid_abc");
        data.put("userEmail", "test@nmims.edu");
        data.put("userName", "Priya");
        data.put("washCount", 4);
        data.put("ironCount", 0);
        data.put("estimatedTotal", 40.0);
        data.put("status", LaundryOrder.STATUS_ACCEPTED);

        LaundryOrder order = LaundryOrder.fromSnapshot("ORD-9999", data);

        assertEquals("ORD-9999", order.getOrderId());
        assertEquals("uid_abc", order.getUserId());
        assertEquals(4, order.getWashCount());
        assertEquals(40.0, order.getEstimatedTotal(), 0.001);
        assertEquals(LaundryOrder.STATUS_ACCEPTED, order.getStatus());
        assertFalse(order.canStudentCancel());
    }

    @Test
    public void testSummaryTextFormatting() {
        LaundryOrder order = new LaundryOrder();
        order.setWashCount(2);
        order.setIronCount(3);
        order.setDryCount(0);
        order.setBedsheetCount(1);

        String summary = order.getItemsSummary();
        assertTrue(summary.contains("Wash: 2"));
        assertTrue(summary.contains("Iron: 3"));
        assertTrue(summary.contains("Bedsheets: 1"));
        assertFalse(summary.contains("Dry clean: 0"));
    }
}
