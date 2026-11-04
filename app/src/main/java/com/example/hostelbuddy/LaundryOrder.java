package com.example.hostelbuddy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model representing a student's laundry order and its complete lifecycle.
 * Conforms to NMIMS Campus Laundry specification.
 */
public class LaundryOrder implements Serializable {

    // Supported statuses per specification (Section 2.4)
    public static final String STATUS_REGISTERED = "Registered";
    public static final String STATUS_RECEIVED = "Order Received";
    public static final String STATUS_RECEIVED_SHORT = "Received";
    public static final String STATUS_WASHING = "Washing";
    public static final String STATUS_DRYING = "Drying";
    public static final String STATUS_IRONING = "Ironing";
    public static final String STATUS_READY = "Ready for Collection";
    public static final String STATUS_COLLECTED = "Collected";
    public static final String STATUS_CANCELLED = "Cancelled";

    // Legacy status aliases for compatibility
    public static final String STATUS_ORDER_RECEIVED_LEGACY = "Order Received";
    public static final String STATUS_ACCEPTED = "Accepted";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_COMPLETED_LEGACY = "Completed";

    private String orderId = "";
    private String requestId = "";
    private String studentUid = "";
    private String sapId = "";
    private String studentName = "";
    private List<Map<String, Object>> items = new ArrayList<>();
    private double estimatedPrice = 0.0;
    private Double finalPrice = null;
    private String status = STATUS_RECEIVED;
    private String remarks = "";
    private String createdBy = "";
    private Date estimatedCompletionAt = null;
    private Date createdAt = null;
    private Date updatedAt = null;

    // Backward-compatibility attributes
    private String userId = "";
    private String userName = "";
    private String userEmail = "";
    private String userRoom = "";
    private String userPhone = "";
    private int washCount = 0;
    private int ironCount = 0;
    private int dryCount = 0;
    private int bedsheetCount = 0;
    private int totalClothesCount = 0;
    private double estimatedTotal = 0.0;
    private double confirmedTotal = 0.0;
    private String paymentStatus = "Unpaid";
    private String service = "Hostel Laundry Service";
    private String studentNotes = "";
    private String adminNotes = "";
    private String pickupSchedule = "";
    private String itemsBreakdownText = "";
    private List<Map<String, Object>> statusHistory = new ArrayList<>();

    public LaundryOrder() { }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        String effectiveStudentUid = !studentUid.isEmpty() ? studentUid : userId;
        String effectiveStudentName = !studentName.isEmpty() ? studentName : userName;
        double effectiveEst = estimatedPrice > 0 ? estimatedPrice : estimatedTotal;
        Double effectiveFinal = finalPrice != null ? finalPrice : (confirmedTotal > 0 ? confirmedTotal : null);

        map.put("orderId", orderId != null ? orderId : "");
        map.put("requestId", requestId != null ? requestId : "");
        map.put("studentUid", effectiveStudentUid != null ? effectiveStudentUid : "");
        map.put("sapId", sapId != null ? sapId : "");
        map.put("studentName", effectiveStudentName != null ? effectiveStudentName : "");
        map.put("items", items != null ? items : new ArrayList<>());
        map.put("estimatedPrice", effectiveEst);
        map.put("finalPrice", effectiveFinal);
        map.put("status", status != null ? status : STATUS_RECEIVED);
        map.put("remarks", remarks != null ? remarks : adminNotes);
        map.put("createdBy", createdBy != null ? createdBy : "");
        if (estimatedCompletionAt != null) {
            map.put("estimatedCompletionAt", estimatedCompletionAt);
        }

        // Backward compatibility entries
        map.put("userId", effectiveStudentUid != null ? effectiveStudentUid : "");
        map.put("userName", effectiveStudentName != null ? effectiveStudentName : "");
        map.put("userEmail", userEmail != null ? userEmail : "");
        map.put("userRoom", userRoom != null ? userRoom : "");
        map.put("userPhone", userPhone != null ? userPhone : "");
        map.put("washCount", washCount);
        map.put("ironCount", ironCount);
        map.put("dryCount", dryCount);
        map.put("bedsheetCount", bedsheetCount);
        map.put("totalClothesCount", getTotalItemCount());
        map.put("estimatedTotal", effectiveEst);
        map.put("confirmedTotal", effectiveFinal != null ? effectiveFinal : effectiveEst);
        map.put("paymentStatus", paymentStatus != null ? paymentStatus : "Unpaid");
        map.put("service", service != null ? service : "Hostel Laundry Service");
        map.put("studentNotes", studentNotes != null ? studentNotes : "");
        map.put("adminNotes", remarks != null && !remarks.isEmpty() ? remarks : adminNotes);
        map.put("pickupSchedule", pickupSchedule != null ? pickupSchedule : "");
        map.put("itemsBreakdownText", getItemsSummary());
        map.put("statusHistory", statusHistory != null ? statusHistory : new ArrayList<>());
        return map;
    }

    public static LaundryOrder fromSnapshot(String id, Map<String, Object> data) {
        LaundryOrder order = new LaundryOrder();
        order.setOrderId(id);
        if (data == null) return order;

        String rid = (String) data.get("requestId");
        if (rid != null) order.setRequestId(rid);

        // Modern schema fields
        String sUid = (String) data.get("studentUid");
        if (sUid == null || sUid.isEmpty()) sUid = (String) data.get("userId");
        order.setStudentUid(sUid != null ? sUid : "");
        order.setUserId(sUid != null ? sUid : "");

        String sap = (String) data.get("sapId");
        if (sap == null && data.get("rollNumber") != null) sap = String.valueOf(data.get("rollNumber"));
        order.setSapId(sap != null ? sap : "");

        String sName = (String) data.get("studentName");
        if (sName == null || sName.isEmpty()) sName = (String) data.get("userName");
        order.setStudentName(sName != null ? sName : "");
        order.setUserName(sName != null ? sName : "");

        order.setUserEmail((String) data.get("userEmail"));
        order.setUserRoom((String) data.get("userRoom"));
        order.setUserPhone((String) data.get("userPhone"));

        Object rawItems = data.get("items");
        if (rawItems instanceof List) {
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (Object obj : (List<?>) rawItems) {
                if (obj instanceof Map) {
                    itemList.add((Map<String, Object>) obj);
                }
            }
            order.setItems(itemList);
        }

        Number estPrice = (Number) data.get("estimatedPrice");
        if (estPrice == null) estPrice = (Number) data.get("estimatedTotal");
        if (estPrice != null) {
            order.setEstimatedPrice(estPrice.doubleValue());
            order.setEstimatedTotal(estPrice.doubleValue());
        }

        Number fPrice = (Number) data.get("finalPrice");
        if (fPrice != null) {
            order.setFinalPrice(fPrice.doubleValue());
            order.setConfirmedTotal(fPrice.doubleValue());
        } else {
            Number conf = (Number) data.get("confirmedTotal");
            if (conf != null) {
                order.setFinalPrice(conf.doubleValue());
                order.setConfirmedTotal(conf.doubleValue());
            } else if (estPrice != null) {
                order.setConfirmedTotal(estPrice.doubleValue());
            }
        }

        String rawStatus = (String) data.get("status");
        order.setStatus(normalizeStatus(rawStatus));

        String rem = (String) data.get("remarks");
        if (rem == null || rem.isEmpty()) rem = (String) data.get("adminNotes");
        order.setRemarks(rem != null ? rem : "");
        order.setAdminNotes(rem != null ? rem : "");

        order.setCreatedBy((String) data.get("createdBy"));

        Number wash = (Number) data.get("washCount");
        if (wash != null) order.setWashCount(wash.intValue());

        Number iron = (Number) data.get("ironCount");
        if (iron != null) order.setIronCount(iron.intValue());

        Number dry = (Number) data.get("dryCount");
        if (dry != null) order.setDryCount(dry.intValue());

        Number bedsheet = (Number) data.get("bedsheetCount");
        if (bedsheet != null) order.setBedsheetCount(bedsheet.intValue());

        Number totalClothes = (Number) data.get("totalClothesCount");
        if (totalClothes != null) order.setTotalClothesCount(totalClothes.intValue());

        String pay = (String) data.get("paymentStatus");
        order.setPaymentStatus(pay != null ? pay : "Unpaid");

        order.setService((String) data.get("service"));
        order.setStudentNotes((String) data.get("studentNotes"));
        order.setPickupSchedule((String) data.get("pickupSchedule"));
        order.setItemsBreakdownText((String) data.get("itemsBreakdownText"));

        Object c = data.get("createdAt");
        if (c instanceof Timestamp) order.setCreatedAt(((Timestamp) c).toDate());
        else if (c instanceof Date) order.setCreatedAt((Date) c);

        Object u = data.get("updatedAt");
        if (u instanceof Timestamp) order.setUpdatedAt(((Timestamp) u).toDate());
        else if (u instanceof Date) order.setUpdatedAt((Date) u);

        Object estComp = data.get("estimatedCompletionAt");
        if (estComp instanceof Timestamp) order.setEstimatedCompletionAt(((Timestamp) estComp).toDate());
        else if (estComp instanceof Date) order.setEstimatedCompletionAt((Date) estComp);

        Object history = data.get("statusHistory");
        if (history instanceof List) {
            order.setStatusHistory((List<Map<String, Object>>) history);
        }

        return order;
    }

    public static String normalizeStatus(String raw) {
        if (raw == null || raw.trim().isEmpty()) return STATUS_RECEIVED;
        String s = raw.trim();
        if (STATUS_ORDER_RECEIVED_LEGACY.equalsIgnoreCase(s) || STATUS_RECEIVED.equalsIgnoreCase(s)) return STATUS_RECEIVED;
        if (STATUS_REGISTERED.equalsIgnoreCase(s)) return STATUS_REGISTERED;
        if (STATUS_WASHING.equalsIgnoreCase(s)) return STATUS_WASHING;
        if (STATUS_DRYING.equalsIgnoreCase(s)) return STATUS_DRYING;
        if (STATUS_IRONING.equalsIgnoreCase(s)) return STATUS_IRONING;
        if (STATUS_READY.equalsIgnoreCase(s)) return STATUS_READY;
        if (STATUS_COLLECTED.equalsIgnoreCase(s)) return STATUS_COLLECTED;
        if (STATUS_COMPLETED.equalsIgnoreCase(s)) return STATUS_COMPLETED;
        if (STATUS_CANCELLED.equalsIgnoreCase(s)) return STATUS_CANCELLED;
        if (STATUS_ACCEPTED.equalsIgnoreCase(s)) return STATUS_ACCEPTED;
        if (STATUS_IN_PROGRESS.equalsIgnoreCase(s)) return STATUS_IN_PROGRESS;
        return s;
    }

    public boolean canStudentCancel() {
        return STATUS_RECEIVED.equalsIgnoreCase(status) || STATUS_ORDER_RECEIVED_LEGACY.equalsIgnoreCase(status);
    }

    public int getTotalItemCount() {
        if (items != null && !items.isEmpty()) {
            int sum = 0;
            for (Map<String, Object> it : items) {
                Object q = it.get("quantity");
                if (q instanceof Number) sum += ((Number) q).intValue();
            }
            if (sum > 0) return sum;
        }
        if (totalClothesCount > 0) return totalClothesCount;
        return washCount + ironCount + dryCount + bedsheetCount;
    }

    public String getItemsSummary() {
        if (items != null && !items.isEmpty()) {
            List<String> list = new ArrayList<>();
            for (Map<String, Object> it : items) {
                String name = (String) it.get("name");
                Object q = it.get("quantity");
                int qty = (q instanceof Number) ? ((Number) q).intValue() : 0;
                if (qty > 0) {
                    list.add((name != null ? name : "Item") + " × " + qty);
                }
            }
            if (!list.isEmpty()) {
                return String.join(" • ", list);
            }
        }
        if (itemsBreakdownText != null && !itemsBreakdownText.trim().isEmpty()) {
            return itemsBreakdownText;
        }
        List<String> parts = new ArrayList<>();
        if (washCount > 0) parts.add("Wash: " + washCount);
        if (ironCount > 0) parts.add("Iron: " + ironCount);
        if (dryCount > 0) parts.add("Dry clean: " + dryCount);
        if (bedsheetCount > 0) parts.add("Bedsheets: " + bedsheetCount);
        if (parts.isEmpty()) return "General Laundry (" + getTotalItemCount() + " items)";
        return String.join(" • ", parts);
    }

    public boolean isConfirmed() {
        return finalPrice != null && finalPrice > 0;
    }

    public double getEffectivePrice() {
        if (finalPrice != null && finalPrice > 0) return finalPrice;
        if (confirmedTotal > 0) return confirmedTotal;
        if (estimatedPrice > 0) return estimatedPrice;
        return estimatedTotal;
    }

    public boolean isActive() {
        String s = status != null ? status : "";
        return !STATUS_COLLECTED.equalsIgnoreCase(s) &&
               !STATUS_CANCELLED.equalsIgnoreCase(s) &&
               !STATUS_COMPLETED_LEGACY.equalsIgnoreCase(s);
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId != null ? requestId : ""; }

    public String getStudentUid() { return studentUid; }
    public void setStudentUid(String studentUid) {
        this.studentUid = studentUid;
        this.userId = studentUid;
    }

    public String getSapId() { return sapId; }
    public void setSapId(String sapId) { this.sapId = sapId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) {
        this.studentName = studentName;
        this.userName = studentName;
    }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public double getEstimatedPrice() { return estimatedPrice > 0 ? estimatedPrice : estimatedTotal; }
    public void setEstimatedPrice(double estimatedPrice) {
        this.estimatedPrice = estimatedPrice;
        this.estimatedTotal = estimatedPrice;
    }

    public Double getFinalPrice() { return finalPrice; }
    public void setFinalPrice(Double finalPrice) {
        this.finalPrice = finalPrice;
        if (finalPrice != null) this.confirmedTotal = finalPrice;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) {
        this.remarks = remarks;
        this.adminNotes = remarks;
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getEstimatedCompletionAt() { return estimatedCompletionAt; }
    public void setEstimatedCompletionAt(Date estimatedCompletionAt) { this.estimatedCompletionAt = estimatedCompletionAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    // Compatibility getters and setters
    public String getUserId() { return !studentUid.isEmpty() ? studentUid : userId; }
    public void setUserId(String userId) {
        this.userId = userId;
        if (this.studentUid.isEmpty()) this.studentUid = userId;
    }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserName() { return !studentName.isEmpty() ? studentName : userName; }
    public void setUserName(String userName) {
        this.userName = userName;
        if (this.studentName.isEmpty()) this.studentName = userName;
    }

    public String getUserRoom() { return userRoom; }
    public void setUserRoom(String userRoom) { this.userRoom = userRoom; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public int getWashCount() { return washCount; }
    public void setWashCount(int washCount) { this.washCount = washCount; }

    public int getIronCount() { return ironCount; }
    public void setIronCount(int ironCount) { this.ironCount = ironCount; }

    public int getDryCount() { return dryCount; }
    public void setDryCount(int dryCount) { this.dryCount = dryCount; }

    public int getBedsheetCount() { return bedsheetCount; }
    public void setBedsheetCount(int bedsheetCount) { this.bedsheetCount = bedsheetCount; }

    public int getTotalClothesCount() { return totalClothesCount; }
    public void setTotalClothesCount(int totalClothesCount) { this.totalClothesCount = totalClothesCount; }

    public double getEstimatedTotal() { return estimatedPrice > 0 ? estimatedPrice : estimatedTotal; }
    public void setEstimatedTotal(double estimatedTotal) {
        this.estimatedTotal = estimatedTotal;
        this.estimatedPrice = estimatedTotal;
    }

    public double getConfirmedTotal() {
        if (finalPrice != null && finalPrice > 0) return finalPrice;
        if (confirmedTotal > 0) return confirmedTotal;
        return getEstimatedTotal();
    }
    public void setConfirmedTotal(double confirmedTotal) {
        this.confirmedTotal = confirmedTotal;
        this.finalPrice = confirmedTotal;
    }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getStudentNotes() { return studentNotes; }
    public void setStudentNotes(String studentNotes) { this.studentNotes = studentNotes; }

    public String getAdminNotes() { return remarks != null && !remarks.isEmpty() ? remarks : adminNotes; }
    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
        this.remarks = adminNotes;
    }

    public String getPickupSchedule() { return pickupSchedule; }
    public void setPickupSchedule(String pickupSchedule) { this.pickupSchedule = pickupSchedule; }

    public String getItemsBreakdownText() { return itemsBreakdownText; }
    public void setItemsBreakdownText(String itemsBreakdownText) { this.itemsBreakdownText = itemsBreakdownText; }

    public List<Map<String, Object>> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<Map<String, Object>> statusHistory) { this.statusHistory = statusHistory; }
}
