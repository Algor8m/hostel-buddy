package com.example.hostelbuddy;

import com.google.firebase.Timestamp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model representing a student-submitted pending laundry request awaiting admin confirmation.
 * Separated from confirmed official orders (laundryOrders) per Section 1-4.
 */
public class LaundryRequest implements Serializable {

    public static final String STATUS_AWAITING_CONFIRMATION = "Awaiting Admin Confirmation";
    public static final String STATUS_CONFIRMED = "Confirmed";
    public static final String STATUS_REJECTED = "Rejected";
    public static final String STATUS_CANCELLED = "Cancelled";

    private String requestId = "";
    private String studentUid = "";
    private String sapId = "";
    private String studentName = "";
    private String userEmail = "";
    private String userRoom = "";
    private String userPhone = "";
    private List<Map<String, Object>> items = new ArrayList<>();
    private int totalClothesCount = 0;
    private double estimatedPrice = 0.0;
    private String status = STATUS_AWAITING_CONFIRMATION;
    private String qrToken = "";
    private String confirmedOrderId = "";
    private String confirmedBy = "";
    private String rejectionReason = "";
    private String remarks = "";
    private Date createdAt = null;
    private Date updatedAt = null;
    private Date confirmedAt = null;

    public LaundryRequest() { }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("requestId", requestId != null ? requestId : "");
        map.put("studentUid", studentUid != null ? studentUid : "");
        map.put("sapId", sapId != null ? sapId : "");
        map.put("studentName", studentName != null ? studentName : "");
        map.put("userEmail", userEmail != null ? userEmail : "");
        map.put("userRoom", userRoom != null ? userRoom : "");
        map.put("userPhone", userPhone != null ? userPhone : "");
        map.put("items", items != null ? items : new ArrayList<>());
        map.put("totalClothesCount", totalClothesCount);
        map.put("estimatedPrice", estimatedPrice);
        map.put("status", status != null ? status : STATUS_AWAITING_CONFIRMATION);
        map.put("qrToken", qrToken != null && !qrToken.isEmpty() ? qrToken : requestId);
        map.put("confirmedOrderId", confirmedOrderId != null ? confirmedOrderId : "");
        map.put("confirmedBy", confirmedBy != null ? confirmedBy : "");
        map.put("rejectionReason", rejectionReason != null ? rejectionReason : "");
        map.put("remarks", remarks != null ? remarks : "");
        if (createdAt != null) map.put("createdAt", createdAt);
        if (updatedAt != null) map.put("updatedAt", updatedAt);
        if (confirmedAt != null) map.put("confirmedAt", confirmedAt);
        return map;
    }

    public static LaundryRequest fromSnapshot(String id, Map<String, Object> data) {
        LaundryRequest req = new LaundryRequest();
        req.setRequestId(id);
        if (data == null) return req;

        String rid = (String) data.get("requestId");
        if (rid != null && !rid.isEmpty()) req.setRequestId(rid);

        req.setStudentUid((String) data.get("studentUid"));
        req.setSapId((String) data.get("sapId"));
        req.setStudentName((String) data.get("studentName"));
        req.setUserEmail((String) data.get("userEmail"));
        req.setUserRoom((String) data.get("userRoom"));
        req.setUserPhone((String) data.get("userPhone"));

        Object rawItems = data.get("items");
        if (rawItems instanceof List) {
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (Object obj : (List<?>) rawItems) {
                if (obj instanceof Map) {
                    itemList.add((Map<String, Object>) obj);
                }
            }
            req.setItems(itemList);
        }

        Number count = (Number) data.get("totalClothesCount");
        if (count != null) req.setTotalClothesCount(count.intValue());

        Number estPrice = (Number) data.get("estimatedPrice");
        if (estPrice != null) req.setEstimatedPrice(estPrice.doubleValue());

        String s = (String) data.get("status");
        if (s != null && !s.trim().isEmpty()) req.setStatus(s.trim());

        String token = (String) data.get("qrToken");
        req.setQrToken(token != null && !token.isEmpty() ? token : id);

        req.setConfirmedOrderId((String) data.get("confirmedOrderId"));
        req.setConfirmedBy((String) data.get("confirmedBy"));
        req.setRejectionReason((String) data.get("rejectionReason"));
        req.setRemarks((String) data.get("remarks"));

        Object c = data.get("createdAt");
        if (c instanceof Timestamp) req.setCreatedAt(((Timestamp) c).toDate());
        else if (c instanceof Date) req.setCreatedAt((Date) c);

        Object u = data.get("updatedAt");
        if (u instanceof Timestamp) req.setUpdatedAt(((Timestamp) u).toDate());
        else if (u instanceof Date) req.setUpdatedAt((Date) u);

        Object conf = data.get("confirmedAt");
        if (conf instanceof Timestamp) req.setConfirmedAt(((Timestamp) conf).toDate());
        else if (conf instanceof Date) req.setConfirmedAt((Date) conf);

        return req;
    }

    public boolean isPending() {
        return STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(status) || "Pending".equalsIgnoreCase(status);
    }

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equalsIgnoreCase(status);
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
        return totalClothesCount > 0 ? (totalClothesCount + " garments") : "No garments specified";
    }

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStudentUid() { return studentUid; }
    public void setStudentUid(String studentUid) { this.studentUid = studentUid != null ? studentUid : ""; }

    public String getSapId() { return sapId; }
    public void setSapId(String sapId) { this.sapId = sapId != null ? sapId : ""; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName != null ? studentName : ""; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail != null ? userEmail : ""; }

    public String getUserRoom() { return userRoom; }
    public void setUserRoom(String userRoom) { this.userRoom = userRoom != null ? userRoom : ""; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone != null ? userPhone : ""; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public int getTotalClothesCount() { return totalClothesCount; }
    public void setTotalClothesCount(int totalClothesCount) { this.totalClothesCount = totalClothesCount; }

    public double getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(double estimatedPrice) { this.estimatedPrice = estimatedPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status != null ? status : STATUS_AWAITING_CONFIRMATION; }

    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken != null ? qrToken : ""; }

    public String getConfirmedOrderId() { return confirmedOrderId; }
    public void setConfirmedOrderId(String confirmedOrderId) { this.confirmedOrderId = confirmedOrderId != null ? confirmedOrderId : ""; }

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy != null ? confirmedBy : ""; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason != null ? rejectionReason : ""; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks != null ? remarks : ""; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public Date getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Date confirmedAt) { this.confirmedAt = confirmedAt; }
}
