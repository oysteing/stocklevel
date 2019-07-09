package net.gisnas.oystein.inventorylevel.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Date;

/**
 * Base inventory model representing inventory level of one item in one location
 *
 * Optimized for quick retrieval, yet low memory footprint
 *
 * Referencing location to avoid duplicate storage of locationId and updatedAt
 *
 * Assuming item SKUs are unique integers (unique across all BUs).
 * SKUs are stored as integers to keep low memory footprint, but formatted
 * with base store specific width in the APIs
 */
public class InventoryLevel {

    public int sku;
    public Location location;
    public int quantity;

    public InventoryLevel(int sku, Location location, int quantity) {
        this.sku = sku;
        this.location = location;
        this.quantity = quantity;
    }

    public String getSku() {
        return String.format(location.baseStore.skuFormat, sku);
    }

    public boolean getAvailable() {
        return quantity > 0;
    }

    public Date getUpdatedAt() {
        if (location.lastUpdatedUTC == null) {
            return new Date();
        } else {
            return location.lastUpdatedUTC;
        }
    }

    @Override
    public String toString() {
        return "sku " + sku + ", " + location + ", " + quantity + " quantity";
    }
}
