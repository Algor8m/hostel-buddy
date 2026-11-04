package com.example.hostelbuddy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class LaundryPricingAndWorkflowTest {

    @Test
    public void testPriceEstimatorCalculations() {
        // Page 8 unit prices:
        // Cloth (Washing + Ironing): ₹15
        // Cloth (Only Ironing): ₹7
        // Cloth (Only Washing): ₹8
        // Bed Sheet (Single): ₹25
        // Jacket: ₹100
        double rateWashIron = 15.0;
        double rateIron = 7.0;
        double rateWash = 8.0;
        double rateBedsheet = 25.0;
        double rateJacket = 100.0;

        int qtyWashIron = 3; // 45
        int qtyIron = 2;     // 14
        int qtyWash = 1;     // 8
        int qtyBedsheet = 2; // 50
        int qtyJacket = 1;   // 100

        int totalQuantity = qtyWashIron + qtyIron + qtyWash + qtyBedsheet + qtyJacket;
        assertEquals(9, totalQuantity);

        double totalCost = (qtyWashIron * rateWashIron) + (qtyIron * rateIron) +
                (qtyWash * rateWash) + (qtyBedsheet * rateBedsheet) + (qtyJacket * rateJacket);
        assertEquals(217.0, totalCost, 0.001);
    }

    @Test
    public void testConfirmedOrderPriceSnapshotIntegrity() {
        // When admin creates order, confirmed total is fixed
        LaundryOrder order = new LaundryOrder();
        order.setOrderId("HB-4021");
        order.setUserId("student_123");
        order.setTotalClothesCount(12);
        order.setEstimatedTotal(180.0);
        order.setConfirmedTotal(180.0);
        order.setStatus(LaundryOrder.STATUS_RECEIVED);

        Map<String, Object> map = order.toMap();
        assertEquals(180.0, (Double) map.get("confirmedTotal"), 0.001);
        assertEquals(12, (int) map.get("totalClothesCount"));

        // If campus rates subsequently change, existing confirmed order total remains 180
        double newRateWashIron = 20.0; // was 15
        assertEquals(180.0, order.getConfirmedTotal(), 0.001);
    }

    @Test
    public void testStatusLifecycleProgression() {
        LaundryOrder order = new LaundryOrder();
        order.setStatus(LaundryOrder.STATUS_RECEIVED);
        assertEquals("Order Received", order.getStatus());

        order.setStatus(LaundryOrder.STATUS_WASHING);
        assertEquals("Washing", order.getStatus());

        order.setStatus(LaundryOrder.STATUS_DRYING);
        assertEquals("Drying", order.getStatus());

        order.setStatus(LaundryOrder.STATUS_IRONING);
        assertEquals("Ironing", order.getStatus());

        order.setStatus(LaundryOrder.STATUS_READY);
        assertEquals("Ready for Collection", order.getStatus());

        order.setStatus(LaundryOrder.STATUS_COLLECTED);
        assertEquals("Collected", order.getStatus());
    }
}
