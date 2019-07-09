package net.gisnas.oystein.inventorylevel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.gisnas.oystein.inventorylevel.model.BaseStore;
import net.gisnas.oystein.inventorylevel.model.InventoryLevel;
import net.gisnas.oystein.inventorylevel.model.InventoryLevelSE;
import net.gisnas.oystein.inventorylevel.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.HttpServerErrorException;

import java.io.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * In-memory database for quick access to inventory levels
 *
 * All inventory levels are stored in an efficient datastructure on-heap, with no backing storage
 * In effect, all inventory levels must fit inside the heap.
 */
@Service
public class InventoryLevelService {
    /**
     * "Normalized storage" - lowest footprint
     */
    private Map<String, Location> locations = new HashMap<>();
    /**
     * Storage optimized for retrieving inventory of one sku at all locations
     */
    private Map<Integer, List<InventoryLevel>> skuMap = new Int2ObjectOpenHashMap<>();

    Logger logger = LoggerFactory.getLogger(InventoryLevelService.class);

    public List<InventoryLevel> getItemAvailability(int sku) {
        return skuMap.get(sku);
    }

    public Location getLocation(String location) {
        return locations.get(location);
    }

    public List<InventoryLevel> inventoryLevels(Integer[] skus, String[] locations) {
        List<InventoryLevel> inventoryLevels = new ArrayList<>();
        if (locations != null && locations.length > 0) {
            for (String location: locations) {
                Location loc = this.locations.get(location);
                if (loc != null) {
                    for (int sku : skus) {
                        InventoryLevel level = loc.inventory.get(sku);
                        if (level != null) {
                            inventoryLevels.add(level);
                        }
                    }
                }
            }
        } else {
            for (int sku : skus) {
                List<InventoryLevel> levels = skuMap.get(sku);
                if (levels != null) {
                    inventoryLevels.addAll(levels);
                }
            }
        }

        return inventoryLevels;
    }

    public void reload() throws InterruptedException, IOException, URISyntaxException {
        logger.debug("Starting reload of all pharmacies");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        locations.putAll(loadAllPharmaciesFromSLQ("http://localhost/fullunrestrictedinventory.json"));
        locations.putAll(loadLloydsSEPharmacies("http://localhost/stock-levels-se"));
        Location beMainWareHouse = loadLloydsBEWarehouse("/home/ogisnas/src/stocklevel/stocks_quotidiens_be_ftp_20181218.csv");
        locations.put(beMainWareHouse.locationId, beMainWareHouse);
        skuMap = createSkuMap(locations);
        stopWatch.stop();
        logger.info("Finished reload of all ({}) pharmacies ({} items) in {}s", locations.size(), skuMap.size(), stopWatch.getTotalTimeSeconds());
    }

    public void reloadLocations(String[] locationIds) throws InterruptedException, IOException, URISyntaxException {
        logger.debug("Starting reload of locations {}", (Object) locationIds);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for (String locationId: locationIds) {
            Location location = loadPharmacyFromSLQ(locationId, "https://slq.vitusapotek.net/pharmacy/" + locationId + "/unrestrictedinventory");
            locations.put(location.locationId, location);
            logger.debug("Loaded {}", location);
        }
        // Remove all existing keys not in reload set
        locations.keySet().retainAll(Set.of(locationIds));
        skuMap = createSkuMap(locations);
        stopWatch.stop();
        logger.info("Finished reload of {} locations ({} items) in {}s", locations.size(), skuMap.size(), stopWatch.getTotalTimeSeconds());

    }

    private Map<String, Location> loadAllPharmaciesFromSLQ(String endpoint) throws IOException, InterruptedException, URISyntaxException {
        HttpResponse<InputStream> response = createRequest(endpoint);
        return parseResponse(response);
    }

    public Location loadPharmacyFromSLQ(String pharmacyId, String endpoint) throws IOException, InterruptedException, URISyntaxException {
        HttpResponse<InputStream> response = createRequest(endpoint);
        if (response.statusCode() != 200) {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Error " + response.statusCode() + " loading pharmacy locations from " + endpoint);
        }
        return parseResponseOneLocation(response);
    }

    public Map<String, Location> loadLloydsSEPharmacies(String endpoint) throws IOException, InterruptedException, URISyntaxException {
        HttpResponse<InputStream> response = createRequest(endpoint);
        return parseResponseSE(response);
    }

    private Location loadLloydsBEWarehouse(String filePath) {
        Location mainWarehouse = new Location(BaseStore.BE_LLOYDSPHARMACIA, "lbe_999010");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filePath))))) {
            // skip CSV header line
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] fragments = line.split(";");
                int sku = Integer.parseInt(fragments[0]);
                int quantityLiege = Integer.parseInt(fragments[3]);
                mainWarehouse.addInventoryLevel(sku, quantityLiege);
            }
        } catch (IOException e) {
            logger.warn("I/O error reading Lloyds BE main warehouse stock file", e);
        }
        return mainWarehouse;
    }

    private HttpResponse<InputStream> createRequest(String endpoint) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().authenticator(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("user", "OTFiNWQ3Y2ZhYjU3NDBkYzRhNjJhZT".toCharArray());
            }
        }).build();
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(endpoint)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private Map<String, Location> parseResponse(HttpResponse<InputStream> response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Location[] locationList = mapper.readValue(response.body(), Location[].class);
        Map<String, Location> newLocations = new HashMap<>(locationList.length);
        for (int i = 0; i < locationList.length; i++) {
            locationList[i].baseStore = BaseStore.NO_VITUSAPOTEK;
            newLocations.put(locationList[i].locationId, locationList[i]);
            logger.debug("Loaded {}", locationList[i]);
        }
        return newLocations;
    }

    private Location parseResponseOneLocation(HttpResponse<InputStream> response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Location location = mapper.readValue(response.body(), Location.class);
        location.baseStore = BaseStore.NO_VITUSAPOTEK;
        return location;
    }

    private Map<String, Location> parseResponseSE(HttpResponse<InputStream> response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InventoryLevelSE[] inventories = mapper.readValue(response.body(), InventoryLevelSE[].class);
        Map<String, Location> newLocations = new HashMap<>();
        for (InventoryLevelSE inventory: inventories) {
            newLocations.computeIfAbsent(inventory.storeNo, k -> new Location(BaseStore.SE_LLOYDSAPOTEK, inventory.storeNo)).addInventoryLevel(inventory.itemNo, inventory.availableStock);
        }
        return newLocations;
    }

    private Map<Integer, List<InventoryLevel>> createSkuMap(Map<String, Location> newLocations) {
        Map<Integer, List<InventoryLevel>> newSkuMap = new Int2ObjectOpenHashMap<>();
        for (Location location : newLocations.values()) {
            for (InventoryLevel inventoryLevel: location.inventory.values()) {
                newSkuMap.computeIfAbsent(inventoryLevel.sku, k -> new ArrayList<>()).add(inventoryLevel);
            }
        }
        for (List<InventoryLevel> inventoryLevels: newSkuMap.values()) {
            ((ArrayList) inventoryLevels).trimToSize();
        }
        return newSkuMap;
    }
}