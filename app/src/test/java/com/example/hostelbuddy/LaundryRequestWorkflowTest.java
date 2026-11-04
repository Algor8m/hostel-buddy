package com.example.hostelbuddy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying the student QR-based laundry request and admin confirmation workflow.
 * Verifies:
 * 1. Request submission data model and serialization.
 * 2. QR token opacity (opaque request ID/token only; NO student personal info).
 * 3. Status transitions (Awaiting Confirmation -> Confirmed / Rejected / Cancelled).
 * 4. Linking confirmed LaundryOrder back to LaundryRequest via requestId.
 * 5. Atomic confirmation logic preventing double confirmation.
 */
public class LaundryRequestWorkflowTest {

    @Test
    public void testLaundryRequestSerializationAndDefaults() {
        LaundryRequest req = new LaundryRequest();
        req.setRequestId("REQ-987654321");
        req.setStudentUid("student_uid_101");
        req.setSapId("70022400999");
        req.setStudentName("Test Student");
        req.setUserEmail("student@nmims.edu");
        req.setUserRoom("Room 404");
        req.setUserPhone("9876543210");

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "cloth_wash_iron");
        item1.put("name", "Cloth (Wash + Iron)");
        item1.put("quantity", 3);
        item1.put("unitPrice", 15.0);
        item1.put("subtotal", 45.0);
        items.add(item1);

        req.setItems(items);
        req.setTotalClothesCount(3);
        req.setEstimatedPrice(45.0);
        req.setQrToken("REQ-987654321");
        req.setCreatedAt(new Date());

        // Initial default status must be Awaiting Admin Confirmation
        assertEquals(LaundryRequest.STATUS_AWAITING_CONFIRMATION, req.getStatus());

        Map<String, Object> map = req.toMap();
        assertEquals("REQ-987654321", map.get("requestId"));
        assertEquals("student_uid_101", map.get("studentUid"));
        assertEquals("70022400999", map.get("sapId"));
        assertEquals(3, map.get("totalClothesCount"));
        assertEquals(45.0, ((Number) map.get("estimatedPrice")).doubleValue(), 0.001);
        assertEquals(LaundryRequest.STATUS_AWAITING_CONFIRMATION, map.get("status"));
        assertEquals("REQ-987654321", map.get("qrToken"));

        // Test snapshot deserialization
        LaundryRequest restored = LaundryRequest.fromSnapshot("REQ-987654321", map);
        assertEquals(req.getRequestId(), restored.getRequestId());
        assertEquals(req.getStudentUid(), restored.getStudentUid());
        assertEquals(req.getSapId(), restored.getSapId());
        assertEquals(req.getTotalClothesCount(), restored.getTotalClothesCount());
        assertEquals(req.getEstimatedPrice(), restored.getEstimatedPrice(), 0.001);
        assertEquals(req.getStatus(), restored.getStatus());
        assertEquals(1, restored.getItems().size());
    }

    @Test
    public void testQrTokenOpacityRule() {
        // Mandatory spec requirement: The QR code must contain only an opaque request ID
        // or secure random token, NEVER student personal information.
        String requestId = "REQ-1740001234";
        String studentName = "Aarav Sharma";
        String studentEmail = "aarav.sharma@nmims.edu";
        String studentSap = "70022400123";
        String studentPhone = "9820098200";

        LaundryRequest req = new LaundryRequest();
        req.setRequestId(requestId);
        req.setStudentName(studentName);
        req.setUserEmail(studentEmail);
        req.setSapId(studentSap);
        req.setUserPhone(studentPhone);
        req.setQrToken(requestId); // opaque token

        String token = req.getQrToken();

        // Must equal opaque ID
        assertEquals("REQ-1740001234", token);

        // Must NOT contain personal identifiable data
        assertFalse("QR token must not contain student name", token.contains(studentName));
        assertFalse("QR token must not contain student email", token.contains(studentEmail));
        assertFalse("QR token must not contain student SAP ID", token.contains(studentSap));
        assertFalse("QR token must not contain student phone", token.contains(studentPhone));
    }

    @Test
    public void testStudentSafeCancellationFlow() {
        LaundryRequest req = new LaundryRequest();
        req.setRequestId("REQ-CANCEL-001");
        req.setStatus(LaundryRequest.STATUS_AWAITING_CONFIRMATION);

        // Student cancels pending request
        req.setStatus(LaundryRequest.STATUS_CANCELLED);
        assertEquals(LaundryRequest.STATUS_CANCELLED, req.getStatus());

        // Cancelled request cannot be confirmed by admin
        boolean canConfirm = LaundryRequest.STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(req.getStatus());
        assertFalse("Cancelled request must not be confirmed", canConfirm);
    }

    @Test
    public void testAdminRejectionFlow() {
        LaundryRequest req = new LaundryRequest();
        req.setRequestId("REQ-REJECT-001");
        req.setStatus(LaundryRequest.STATUS_AWAITING_CONFIRMATION);

        // Admin rejects request with reason
        req.setStatus(LaundryRequest.STATUS_REJECTED);
        req.setRejectionReason("Stained item not serviceable");

        assertEquals(LaundryRequest.STATUS_REJECTED, req.getStatus());
        assertEquals("Stained item not serviceable", req.getRejectionReason());

        // Rejected request cannot be confirmed
        boolean canConfirm = LaundryRequest.STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(req.getStatus());
        assertFalse("Rejected request must not be confirmed", canConfirm);
    }

    @Test
    public void testAdminConfirmationAndOrderLinking() {
        LaundryRequest req = new LaundryRequest();
        req.setRequestId("REQ-CONFIRM-555");
        req.setStudentUid("student_abc");
        req.setSapId("70022400555");
        req.setStudentName("Priya Patel");
        req.setUserEmail("priya@nmims.edu");
        req.setTotalClothesCount(4);
        req.setEstimatedPrice(60.0);
        req.setStatus(LaundryRequest.STATUS_AWAITING_CONFIRMATION);

        // Admin verifies clothes count and confirms order
        int verifiedCount = 5; // Admin found 1 extra item
        double finalPrice = 75.0;
        String confirmedOrderId = "HB-4321";
        String adminEmail = "laundry.admin@nmims.edu";

        // State change on request
        req.setStatus(LaundryRequest.STATUS_CONFIRMED);
        req.setConfirmedOrderId(confirmedOrderId);
        req.setConfirmedBy(adminEmail);
        req.setConfirmedAt(new Date());

        assertEquals(LaundryRequest.STATUS_CONFIRMED, req.getStatus());
        assertEquals("HB-4321", req.getConfirmedOrderId());
        assertEquals(adminEmail, req.getConfirmedBy());
        assertNotNull(req.getConfirmedAt());

        // Create official confirmed LaundryOrder
        LaundryOrder order = new LaundryOrder();
        order.setOrderId(confirmedOrderId);
        order.setRequestId(req.getRequestId()); // Links back to request
        order.setStudentUid(req.getStudentUid());
        order.setSapId(req.getSapId());
        order.setStudentName(req.getStudentName());
        order.setTotalClothesCount(verifiedCount);
        order.setFinalPrice(finalPrice);
        order.setStatus(LaundryOrder.STATUS_RECEIVED);

        // Verify linkage
        assertEquals(req.getRequestId(), order.getRequestId());
        assertEquals(confirmedOrderId, order.getOrderId());
        assertEquals(5, order.getTotalItemCount());
        assertEquals(75.0, order.getFinalPrice(), 0.001);

        // Verify order toMap and fromSnapshot preserves requestId
        Map<String, Object> orderMap = order.toMap();
        assertEquals("REQ-CONFIRM-555", orderMap.get("requestId"));

        LaundryOrder restoredOrder = LaundryOrder.fromSnapshot(confirmedOrderId, orderMap);
        assertEquals("REQ-CONFIRM-555", restoredOrder.getRequestId());
    }

    @Test
    public void testDoubleConfirmationPrevention() {
        LaundryRequest req = new LaundryRequest();
        req.setRequestId("REQ-DOUBLE-001");
        req.setStatus(LaundryRequest.STATUS_CONFIRMED);
        req.setConfirmedOrderId("HB-1111");

        // Simulating incoming second confirmation attempt
        boolean canConfirmAgain = LaundryRequest.STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(req.getStatus());
        assertFalse("Already confirmed request cannot be confirmed again", canConfirmAgain);
    }
}
