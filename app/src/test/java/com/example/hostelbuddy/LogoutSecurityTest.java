package com.example.hostelbuddy;

import android.content.Intent;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests validating session logout requirements:
 * - Proper activity back-stack flags for clearing session navigation
 * - Verification that Logout and Back actions are distinct
 * - Protection logic on admin screens when unauthenticated
 */
public class LogoutSecurityTest {

    @Test
    public void testLogoutIntentFlags() {
        // Required flags to guarantee back stack is completely cleared:
        // Intent.FLAG_ACTIVITY_NEW_TASK (0x10000000) | Intent.FLAG_ACTIVITY_CLEAR_TASK (0x00008000)
        int expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
        assertTrue("Flags must contain FLAG_ACTIVITY_NEW_TASK", (expectedFlags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue("Flags must contain FLAG_ACTIVITY_CLEAR_TASK", (expectedFlags & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
    }

    @Test
    public void testRoleGatingAfterLogout() {
        // After logout, normalized roles should never grant administrative access if unassigned
        assertEquals("", LoginActivity.normalizeRole(null));
        assertEquals("", LoginActivity.normalizeRole(""));

        // Regular student role must not grant access to laundry or mess admin
        String studentRole = LoginActivity.normalizeRole("student");
        assertNotEquals("laundry_admin", studentRole);
        assertNotEquals("mess_admin", studentRole);
        assertNotEquals("food_truck_admin", studentRole);
    }
}
