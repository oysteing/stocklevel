package net.gisnas.oystein.inventorylevel.controller;

import net.gisnas.oystein.inventorylevel.model.InventoryLevel;
import net.gisnas.oystein.inventorylevel.model.Location;
import net.gisnas.oystein.inventorylevel.service.InventoryLevelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class InventoryLevelApi {
    Logger logger = LoggerFactory.getLogger(InventoryLevelApi.class);

    @Autowired
    InventoryLevelService inventory;

    /**
     * Availability of item in all locations
     *
     * @param sku Item identifier
     * @return Availability information
     */
    @RequestMapping("/item/{sku}/availability")
    public ResponseEntity<List<InventoryLevel>> inventoryLevelOneItem(@PathVariable int sku) {
        logger.debug("Request for availability of sku {}", sku);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES)).body(inventory.getItemAvailability(sku));
    }

    /** Availability of one item at one location
     *
     * @param location Location identifier - location operating license number
     * @param sku Item identifier (varenummer)
     * @return Availability information
     */
    @RequestMapping("/location/{location}/item/{sku}/availability")
    public ResponseEntity<InventoryLevel> inventoryLevelOneLocationOneItem(@PathVariable String location, @PathVariable Integer sku) {
        logger.debug("Request for availability of sku {} at location {}", sku, location);
        Location loc = inventory.getLocation(location);
        if (loc == null) {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "location="+location);
        }
        InventoryLevel level = loc.inventory.get(sku);
        if (level == null) {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "sku="+sku);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(maxAge(level.getUpdatedAt()), TimeUnit.SECONDS)).body(level);
    }

    /**
     * Availability of multiple items at multiple locations
     *
     * @param skus Item identifiers - comma separated list of one or more varenummer
     * @param locations Location identifiers - optional, comma separated list of location operating license numbers (ex: 1234) to filter by
     * @return Availability information
     */
    @RequestMapping("/inventory_levels")
    public ResponseEntity<List<InventoryLevel>> inventoryLevels(@RequestParam Integer[] skus, @RequestParam(required = false) String[] locations) {
        logger.debug("Request for availability of skus {} at {}", skus, locations);
        List<InventoryLevel> inventoryLevels = inventory.inventoryLevels(skus, locations);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES)).body(inventoryLevels);
    }

    @RequestMapping("/reload")
    public synchronized void reload() throws IOException, URISyntaxException, InterruptedException {
        inventory.reload();
    }

    @RequestMapping("/reload_locations")
    public synchronized void reloadLocations(@RequestParam("locations") String[] locationIds) throws IOException, URISyntaxException, InterruptedException {
        inventory.reloadLocations(locationIds);
    }

    /**
     * Calculate remaining time until next update, assuming update every 5 min
     *
     * @param resourceDate Date of last update
     * @return Number of seconds until next update
     */
    private long maxAge(Date resourceDate) {
        return 300 - (new Date().getTime() - resourceDate.getTime())/1000;
    }
}