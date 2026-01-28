package com.autostartstop.server;

import com.autostartstop.Log;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks server startup times and calculates expected startup durations
 * based on historical data.
 * 
 * Data is persisted to a file and trimmed periodically to prevent excessive file size.
 */
public class StartupTimeTracker {
    private static final Logger logger = Log.get(StartupTimeTracker.class);
    
    private static final String DATA_FILE_NAME = "startup_times.dat";
    private static final int MAX_ENTRIES_PER_SERVER = 20;
    private static final Duration DEFAULT_EXPECTED_TIME = Duration.ofSeconds(30);
    
    private final Path dataFile;
    private final Map<String, List<Long>> startupTimes = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public StartupTimeTracker(Path dataDirectory) {
        this.dataFile = dataDirectory.resolve(DATA_FILE_NAME);
        loadData();
    }

    /**
     * Records a startup time for a server.
     * 
     * @param serverName The server name
     * @param durationMs The startup duration in milliseconds
     */
    public void recordStartupTime(String serverName, long durationMs) {
        if (durationMs <= 0) {
            logger.debug("Ignoring invalid startup time {}ms for server '{}'", durationMs, serverName);
            return;
        }
        
        logger.debug("Recording startup time {}ms for server '{}'", durationMs, serverName);
        
        List<Long> times = startupTimes.computeIfAbsent(serverName, k -> new ArrayList<>());
        synchronized (times) {
            times.add(durationMs);
            
            // Trim if exceeds max entries
            while (times.size() > MAX_ENTRIES_PER_SERVER) {
                times.remove(0);
            }
        }
        
        // Save asynchronously
        saveDataAsync();
    }

    /**
     * Gets the calculated expected startup time for a server based on historical data.
     * Uses a weighted moving average with recent times having more weight.
     * 
     * @param serverName The server name
     * @return The calculated expected startup duration, or default if no data
     */
    public Duration getExpectedStartupTime(String serverName) {
        List<Long> times = startupTimes.get(serverName);
        if (times == null || times.isEmpty()) {
            logger.debug("No startup time data for server '{}', using default {}s", 
                    serverName, DEFAULT_EXPECTED_TIME.getSeconds());
            return DEFAULT_EXPECTED_TIME;
        }
        
        synchronized (times) {
            if (times.isEmpty()) {
                return DEFAULT_EXPECTED_TIME;
            }
            
            // Use weighted moving average - recent times have more weight
            double weightedSum = 0;
            double weightTotal = 0;
            int size = times.size();
            
            for (int i = 0; i < size; i++) {
                // Weight increases linearly with index (older = lower weight)
                double weight = (i + 1.0) / size;
                weightedSum += times.get(i) * weight;
                weightTotal += weight;
            }
            
            long avgMs = (long) (weightedSum / weightTotal); 
            
            logger.debug("Calculated expected startup time for '{}': {}ms (from {} samples)", 
                    serverName, avgMs, size);
            
            return Duration.ofMillis(avgMs);
        }
    }

    /**
     * Checks if there is historical data for a server.
     * 
     * @param serverName The server name
     * @return true if there is startup time data
     */
    public boolean hasData(String serverName) {
        List<Long> times = startupTimes.get(serverName);
        return times != null && !times.isEmpty();
    }

    /**
     * Gets the number of recorded startup times for a server.
     * 
     * @param serverName The server name
     * @return The number of recorded times
     */
    public int getRecordCount(String serverName) {
        List<Long> times = startupTimes.get(serverName);
        return times != null ? times.size() : 0;
    }

    /**
     * Loads data from the data file.
     */
    private void loadData() {
        synchronized (fileLock) {
            if (!Files.exists(dataFile)) {
                logger.debug("Startup times data file does not exist: {}", dataFile);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(dataFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // Format: serverName:time1,time2,time3,...
                    int colonIndex = line.indexOf(':');
                    if (colonIndex <= 0) {
                        continue;
                    }
                    
                    String serverName = line.substring(0, colonIndex);
                    String timesStr = line.substring(colonIndex + 1);
                    
                    List<Long> times = new ArrayList<>();
                    for (String timeStr : timesStr.split(",")) {
                        try {
                            long time = Long.parseLong(timeStr.trim());
                            if (time > 0) {
                                times.add(time);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid entries
                        }
                    }
                    
                    if (!times.isEmpty()) {
                        startupTimes.put(serverName, times);
                        logger.debug("Loaded {} startup times for server '{}'", times.size(), serverName);
                    }
                }
                
                logger.debug("Loaded startup time data for {} servers", startupTimes.size());
                
            } catch (IOException e) {
                logger.warn("Failed to load startup times data: {}", e.getMessage());
            }
        }
    }

    /**
     * Saves data to the data file asynchronously.
     */
    private void saveDataAsync() {
        Thread.startVirtualThread(this::saveData);
    }

    /**
     * Saves data to the data file.
     */
    private void saveData() {
        synchronized (fileLock) {
            try {
                // Ensure parent directory exists
                Files.createDirectories(dataFile.getParent());
                
                try (BufferedWriter writer = Files.newBufferedWriter(dataFile)) {
                    writer.write("# AutoStartStop startup times data\n");
                    writer.write("# Format: serverName:time1,time2,time3,...\n");
                    writer.write("# Times are in milliseconds\n\n");
                    
                    for (Map.Entry<String, List<Long>> entry : startupTimes.entrySet()) {
                        String serverName = entry.getKey();
                        List<Long> times = entry.getValue();
                        
                        synchronized (times) {
                            if (times.isEmpty()) {
                                continue;
                            }
                            
                            StringBuilder sb = new StringBuilder();
                            sb.append(serverName).append(":");
                            for (int i = 0; i < times.size(); i++) {
                                if (i > 0) {
                                    sb.append(",");
                                }
                                sb.append(times.get(i));
                            }
                            sb.append("\n");
                            writer.write(sb.toString());
                        }
                    }
                }
                
                logger.debug("Saved startup time data for {} servers", startupTimes.size());
                
            } catch (IOException e) {
                logger.warn("Failed to save startup times data: {}", e.getMessage());
            }
        }
    }

    /**
     * Clears all data for a server.
     * 
     * @param serverName The server name
     */
    public void clearData(String serverName) {
        startupTimes.remove(serverName);
        saveDataAsync();
        logger.debug("Cleared startup time data for server '{}'", serverName);
    }

    /**
     * Clears all data.
     */
    public void clearAllData() {
        startupTimes.clear();
        saveDataAsync();
        logger.debug("Cleared all startup time data");
    }
}
