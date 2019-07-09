package net.gisnas.oystein.inventorylevel.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import it.unimi.dsi.fastutil.ints.*;

import java.util.Date;
import java.util.Map;

/**
 * Inventory location, typically a pharmacy or warehouse
 */
public class Location {

    @JsonValue
    public String locationId;
    public Date lastUpdatedUTC;
    public Map<Integer, InventoryLevel> inventory;
    public BaseStore baseStore;

    /**
     * Constructor for deserializing SLQ data model
     *
     * @param pharmacyId
     * @param lastUpdatedUTC
     * @param stockLevels
     */
    @JsonCreator
    public Location(@JsonProperty("pharmacyId") String pharmacyId, @JsonProperty("lastUpdatedUTC") Date lastUpdatedUTC, @JsonProperty("stockLevels") InventoryLevelNO[] stockLevels) {
        this.locationId = pharmacyId;
        this.lastUpdatedUTC = lastUpdatedUTC;
        this.inventory = new Int2ObjectOpenHashMap<>(stockLevels.length);
        for (int i = 0; i < stockLevels.length; i++) {
            this.inventory.put(stockLevels[i].sku, new InventoryLevel(stockLevels[i].sku, this, stockLevels[i].quantity));
        }
    }

    public Location(BaseStore baseStore, String storeNo) {
        this.baseStore = baseStore;
        this.locationId = storeNo;
        inventory = new Int2ObjectOpenHashMap<>();
    }

    public void addInventoryLevel(int itemNo, int availableStock) {
        int sku = itemNo;
        inventory.put(sku, new InventoryLevel(sku, this, availableStock));
    }

    public String toString() {
        return "Location " + locationId + " (updated " + lastUpdatedUTC + "), " + inventory.size() + " inventory levels";
    }

}