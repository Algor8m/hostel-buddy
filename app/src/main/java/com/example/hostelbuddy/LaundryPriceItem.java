package com.example.hostelbuddy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Model representing a garment or service price item in the laundry catalog.
 */
public class LaundryPriceItem implements Serializable {

    private String id;
    private String name;
    private double unitPrice;
    private boolean active = true;

    public LaundryPriceItem() { }

    public LaundryPriceItem(String id, String name, double unitPrice) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.active = true;
    }

    public LaundryPriceItem(String id, String name, double unitPrice, boolean active) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.active = active;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id != null ? id : "");
        map.put("name", name != null ? name : "");
        map.put("unitPrice", unitPrice);
        map.put("active", active);
        return map;
    }

    public static LaundryPriceItem fromMap(String docId, Map<String, Object> data) {
        LaundryPriceItem item = new LaundryPriceItem();
        item.setId(docId);
        if (data == null) return item;

        String n = (String) data.get("name");
        item.setName(n != null ? n : docId);

        Number price = (Number) data.get("unitPrice");
        if (price == null) price = (Number) data.get("price");
        if (price != null) item.setUnitPrice(price.doubleValue());

        Boolean act = (Boolean) data.get("active");
        item.setActive(act == null || act);

        return item;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
