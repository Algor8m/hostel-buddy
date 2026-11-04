package com.example.hostelbuddy;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests verifying tightened Firestore security rules logic for Laundry Orders:
 * 1. Authorized Laundry Admin creation/update with verified roles and explicitly authorized admin accounts.
 * 2. Strict rejection of students (create, update, delete).
 * 3. Strict rejection of other department administrators (mess_admin, food_truck_admin).
 * 4. Strict rejection of generic 'admin' claims or arbitrary domain emails without verified role.
 * 5. Student restricted read access (only own orders).
 * 6. Order document schema and status lifecycle validation.
 */
public class FirestoreLaundryOrderSecurityTest {

    private static final Set<String> ALLOWED_LAUNDRY_ADMIN_ROLES = new HashSet<>(Arrays.asList(
            "laundry_admin", "Laundry Admin", "laundry-admin", "laundry", "Laundry",
            "super_admin", "Super Admin", "superadmin"
    ));

    private static final Set<String> EXPLICIT_LAUNDRY_ADMIN_EMAILS = new HashSet<>(Arrays.asList(
            "laundry.admin@hostelbuddy.com",
            "laundry@hostelbuddy.com",
            "laundry_admin@hostelbuddy.com",
            "laundry.admin@nmims.edu",
            "laundry@nmims.edu",
            "laundry_admin@nmims.edu",
            "admin@hostelbuddy.com"
    ));

    private static final Set<String> ALLOWED_STATUSES = new HashSet<>(Arrays.asList(
            "Registered", "registered",
            "Order Received", "order received",
            "Received", "received",
            "Washing", "washing",
            "Drying", "drying",
            "Ironing", "ironing",
            "Ready for Collection", "ready for collection",
            "Collected", "collected",
            "Accepted", "accepted",
            "In Progress", "in progress",
            "Completed", "completed",
            "Cancelled", "cancelled"
    ));

    /**
     * Simulates tightened isLaundryAdmin() security rules helper.
     */
    private boolean simulateIsLaundryAdmin(String userDocRole, String tokenRole, String tokenEmail, boolean isLaundryAdminClaim) {
        // 1. Verified Firestore user document role
        if (userDocRole != null && ALLOWED_LAUNDRY_ADMIN_ROLES.contains(userDocRole)) {
            return true;
        }
        // 2. Verified role-specific Firebase Auth Custom Claims
        if (tokenRole != null && ("laundry_admin".equals(tokenRole) || "super_admin".equals(tokenRole))) {
            return true;
        }
        if (isLaundryAdminClaim) {
            return true;
        }
        // 3. Explicitly authorized administrator accounts (no generic domain regex or generic admin claim)
        if (tokenEmail != null && EXPLICIT_LAUNDRY_ADMIN_EMAILS.contains(tokenEmail.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    /**
     * Simulates isValidLaundryOrder(data) security rules helper.
     */
    private boolean simulateIsValidLaundryOrder(Map<String, Object> data) {
        if (data == null) return false;
        Object statusObj = data.get("status");
        if (!(statusObj instanceof String)) return false;
        String status = (String) statusObj;
        if (!ALLOWED_STATUSES.contains(status)) return false;

        Object estPriceObj = data.get("estimatedPrice");
        Object estTotalObj = data.get("estimatedTotal");

        boolean hasValidEstPrice = (estPriceObj instanceof Number) && ((Number) estPriceObj).doubleValue() >= 0;
        boolean hasValidEstTotal = (estTotalObj instanceof Number) && ((Number) estTotalObj).doubleValue() >= 0;

        return hasValidEstPrice || hasValidEstTotal;
    }

    /**
     * Simulates allow create rule for /laundryOrders/{orderId}.
     */
    private boolean simulateAllowCreateOrder(String authUid, String userDocRole, String tokenRole, String tokenEmail, boolean isLaundryAdminClaim, Map<String, Object> orderData) {
        if (authUid == null || authUid.trim().isEmpty()) return false; // isAuthenticated() required
        boolean isLaundryAdmin = simulateIsLaundryAdmin(userDocRole, tokenRole, tokenEmail, isLaundryAdminClaim);
        return isLaundryAdmin && simulateIsValidLaundryOrder(orderData);
    }

    /**
     * Simulates allow update rule for /laundryOrders/{orderId}.
     */
    private boolean simulateAllowUpdateOrder(String authUid, String userDocRole, String tokenRole, String tokenEmail) {
        if (authUid == null || authUid.trim().isEmpty()) return false;
        return simulateIsLaundryAdmin(userDocRole, tokenRole, tokenEmail, false);
    }

    /**
     * Simulates allow read rule for /laundryOrders/{orderId}.
     */
    private boolean simulateAllowReadOrder(String authUid, String userDocRole, String tokenEmail, Map<String, Object> orderData) {
        if (authUid == null || authUid.trim().isEmpty()) return false;
        boolean isLaundryAdmin = simulateIsLaundryAdmin(userDocRole, null, tokenEmail, false);
        if (isLaundryAdmin) return true;

        String studentUid = (String) orderData.get("studentUid");
        String userId = (String) orderData.get("userId");
        return (studentUid != null && studentUid.equals(authUid)) ||
               (userId != null && userId.equals(authUid));
    }

    @Test
    public void testAuthorizedLaundryAdminOrderCreation() {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", "HB-5521");
        orderData.put("studentUid", "student_uid_101");
        orderData.put("sapId", "0070022400123");
        orderData.put("studentName", "Arya Stark");
        orderData.put("status", LaundryOrder.STATUS_RECEIVED);
        orderData.put("estimatedPrice", 120.0);
        orderData.put("estimatedTotal", 120.0);

        // 1. Admin with verified role="laundry_admin" in user document
        assertTrue(simulateAllowCreateOrder("admin_uid_1", "laundry_admin", null, null, false, orderData));

        // 2. Admin with verified role="Laundry Admin" in user document
        assertTrue(simulateAllowCreateOrder("admin_uid_2", "Laundry Admin", null, null, false, orderData));

        // 3. Admin with verified role="laundry-admin" in user document
        assertTrue(simulateAllowCreateOrder("admin_uid_3", "laundry-admin", null, null, false, orderData));

        // 4. Super Admin
        assertTrue(simulateAllowCreateOrder("super_uid", "super_admin", null, null, false, orderData));

        // 5. Admin with role-specific custom claim
        assertTrue(simulateAllowCreateOrder("claim_admin_uid", null, "laundry_admin", null, false, orderData));
        assertTrue(simulateAllowCreateOrder("claim_admin_uid2", null, null, null, true, orderData));

        // 6. Admin authenticated via explicitly authorized account
        assertTrue(simulateAllowCreateOrder("admin_uid_email", null, null, "laundry.admin@hostelbuddy.com", false, orderData));
        assertTrue(simulateAllowCreateOrder("admin_uid_nmims", null, null, "laundry.admin@nmims.edu", false, orderData));
    }

    @Test
    public void testUnauthorizedStudentOrderCreationDenied() {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", "HB-5522");
        orderData.put("studentUid", "student_uid_101");
        orderData.put("sapId", "0070022400123");
        orderData.put("status", LaundryOrder.STATUS_RECEIVED);
        orderData.put("estimatedPrice", 50.0);

        // Students must NEVER be permitted to create official laundry orders (Section 4 & Rules)
        assertFalse("Student with role 'student' must be denied order creation",
                simulateAllowCreateOrder("student_uid_101", "student", null, "student@nmims.edu", false, orderData));
        assertFalse("Student with role 'Student' must be denied order creation",
                simulateAllowCreateOrder("student_uid_101", "Student", null, "student@hostelbuddy.com", false, orderData));

        // Unauthenticated access must be denied
        assertFalse("Unauthenticated request must be denied",
                simulateAllowCreateOrder(null, null, null, null, false, orderData));
    }

    @Test
    public void testUnauthorizedAdministratorOrderCreationDenied() {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", "HB-5523");
        orderData.put("studentUid", "student_uid_102");
        orderData.put("sapId", "0070022400456");
        orderData.put("status", LaundryOrder.STATUS_RECEIVED);
        orderData.put("estimatedPrice", 80.0);

        // 1. Other department admins must NOT create laundry orders
        assertFalse("Mess Admin cannot create laundry orders",
                simulateAllowCreateOrder("mess_uid", "mess_admin", null, "mess@hostelbuddy.com", false, orderData));
        assertFalse("Food Truck Admin cannot create laundry orders",
                simulateAllowCreateOrder("cc_uid", "food_truck_admin", null, "cravings@hostelbuddy.com", false, orderData));

        // 2. Generic 'admin' claim without specific laundry role must be DENIED
        assertFalse("Generic admin role claim must be denied laundry order creation",
                simulateAllowCreateOrder("generic_admin", null, "admin", null, false, orderData));

        // 3. Arbitrary email on domain without verified role must be DENIED
        assertFalse("Arbitrary email on nmims.edu without verified role must be denied",
                simulateAllowCreateOrder("random_user", null, null, "faculty@nmims.edu", false, orderData));
        assertFalse("Arbitrary email on hostelbuddy.com without verified role must be denied",
                simulateAllowCreateOrder("random_staff", null, null, "staff@hostelbuddy.com", false, orderData));
    }

    @Test
    public void testStudentOrderModificationDenied() {
        // Students must NOT modify laundry orders
        assertFalse(simulateAllowUpdateOrder("student_uid_101", "student", null, "student@nmims.edu"));
        assertFalse(simulateAllowUpdateOrder(null, null, null, null));

        // Mess admin or Food truck admin must NOT modify laundry orders
        assertFalse(simulateAllowUpdateOrder("mess_uid", "mess_admin", null, "mess@hostelbuddy.com"));
        assertFalse(simulateAllowUpdateOrder("cc_uid", "food_truck_admin", null, "cravings@hostelbuddy.com"));

        // Laundry Admin CAN modify laundry orders
        assertTrue(simulateAllowUpdateOrder("admin_uid_1", "laundry_admin", null, null));
        assertTrue(simulateAllowUpdateOrder("admin_uid_2", "Laundry Admin", null, null));
    }

    @Test
    public void testStudentRestrictedReadAccess() {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", "HB-9901");
        orderData.put("studentUid", "student_uid_alpha");
        orderData.put("userId", "student_uid_alpha");
        orderData.put("status", LaundryOrder.STATUS_WASHING);

        // Student can read their own order
        assertTrue("Student should be allowed to read their own order",
                simulateAllowReadOrder("student_uid_alpha", "student", "alpha@nmims.edu", orderData));

        // Another student CANNOT read this order
        assertFalse("Student must be denied reading another student's order",
                simulateAllowReadOrder("student_uid_beta", "student", "beta@nmims.edu", orderData));

        // Unauthenticated read must be denied
        assertFalse("Unauthenticated user cannot read orders",
                simulateAllowReadOrder(null, null, null, orderData));

        // Laundry Admin can read any order
        assertTrue("Laundry Admin can read any student order",
                simulateAllowReadOrder("admin_uid", "laundry_admin", null, orderData));
    }

    @Test
    public void testOrderDocumentValidation() {
        // Valid order
        Map<String, Object> validOrder = new HashMap<>();
        validOrder.put("status", "Received");
        validOrder.put("estimatedPrice", 75.0);
        assertTrue(simulateIsValidLaundryOrder(validOrder));

        // Missing status
        Map<String, Object> noStatus = new HashMap<>();
        noStatus.put("estimatedPrice", 75.0);
        assertFalse(simulateIsValidLaundryOrder(noStatus));

        // Invalid status
        Map<String, Object> invalidStatus = new HashMap<>();
        invalidStatus.put("status", "HACKED_STATUS");
        invalidStatus.put("estimatedPrice", 75.0);
        assertFalse(simulateIsValidLaundryOrder(invalidStatus));

        // Negative price
        Map<String, Object> negativePrice = new HashMap<>();
        negativePrice.put("status", "Received");
        negativePrice.put("estimatedPrice", -10.0);
        assertFalse(simulateIsValidLaundryOrder(negativePrice));

        // Valid with legacy field estimatedTotal
        Map<String, Object> legacyValid = new HashMap<>();
        legacyValid.put("status", "Order Received");
        legacyValid.put("estimatedTotal", 0.0);
        assertTrue(simulateIsValidLaundryOrder(legacyValid));
    }
}
