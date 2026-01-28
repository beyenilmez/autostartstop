package com.autostartstop.server;

import com.autostartstop.Log;
import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.util.DurationUtil;
import com.autostartstop.util.MiniMessageUtil;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages MOTD caching for virtual hosts.
 * Caches MOTD responses from servers and stores them in MiniMessage format.
 */
public class MotdCacheManager {
    private static final Logger logger = Log.get(MotdCacheManager.class);
    
    private final ProxyServer proxy;
    private final ServerManager serverManager;
    private final Path cacheFile;
    private final Duration cacheInterval;
    
    private volatile com.velocitypowered.api.scheduler.ScheduledTask cacheTask;
    private final Map<String, String> motdCache = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();
    
    public MotdCacheManager(ProxyServer proxy, ServerManager serverManager, Path dataDirectory, Duration cacheInterval) {
        this.proxy = proxy;
        this.serverManager = serverManager;
        this.cacheFile = dataDirectory.resolve("motd_cache.dat");
        this.cacheInterval = cacheInterval;
    }
    
    /**
     * Checks if periodic MOTD caching is enabled (interval > 0).
     * 
     * @return true if cache interval is greater than 0
     */
    public boolean isPeriodicCacheEnabled() {
        return cacheInterval != null && cacheInterval.toMillis() > 0;
    }
    
    /**
     * Starts the MOTD cache manager.
     * Always performs initial cache and loads existing cache from file.
     * Only schedules periodic cache task if interval > 0.
     * 
     * @param plugin The plugin instance
     */
    public void start(Object plugin) {
        logger.debug("Starting MOTD cache manager (interval: {})", 
                cacheInterval != null ? DurationUtil.format(cacheInterval) : "disabled");
        
        // Load existing cache from file
        loadCache();
        
        // Perform initial cache
        cacheMotds();
        
        // Schedule periodic cache only if interval > 0
        if (isPeriodicCacheEnabled()) {
            cacheTask = proxy.getScheduler()
                    .buildTask(plugin, this::cacheMotds)
                    .delay(cacheInterval)
                    .repeat(cacheInterval)
                    .schedule();
            
            logger.info("MOTD cache manager started with periodic updates (interval: {})", 
                    DurationUtil.format(cacheInterval));
        } else {
            logger.info("MOTD cache manager started (initial cache only, no periodic updates)");
        }
    }
    
    /**
     * Stops the periodic MOTD cache task.
     */
    public void stop() {
        if (cacheTask != null) {
            cacheTask.cancel();
            cacheTask = null;
        }
        
        // Save cache to file before stopping
        saveCache();
        
        logger.debug("MOTD cache manager stopped");
    }
    
    /**
     * Saves data to the data file asynchronously.
     */
    private void saveCacheAsync() {
        Thread.startVirtualThread(this::saveCache);
    }
    
    /**
     * Gets cached MOTD for a virtual host.
     * If no virtual host is provided, returns the cached MOTD under "default".
     * 
     * @param virtualHost The virtual host name (null or empty for default)
     * @return The cached MOTD string, or null if not cached
     */
    public String getCachedMotd(String virtualHost) {
        String key = (virtualHost == null || virtualHost.isEmpty()) ? "default" : virtualHost;
        return motdCache.get(key);
    }
    
    /**
     * Caches MOTD for a specific server by name.
     * This is useful for caching MOTD before a server stops.
     * 
     * @param serverName The name of the server to cache MOTD for
     * @return true if MOTD was successfully cached, false otherwise
     */
    public boolean cacheMotdForServer(String serverName) {
        PluginConfig config = serverManager.getPluginConfig();
        if (config == null || config.getServers() == null) {
            logger.debug("No servers configured, cannot cache MOTD for server '{}'", serverName);
            return false;
        }
        
        ServerConfig serverConfig = config.getServers().get(serverName);
        if (serverConfig == null) {
            logger.debug("Server '{}' not found in configuration, cannot cache MOTD", serverName);
            return false;
        }
        
        String virtualHost = serverConfig.getVirtualHost();
        String cacheKey = (virtualHost == null || virtualHost.isEmpty()) ? "default" : virtualHost;
        
        RegisteredServer registeredServer = serverManager.getRegisteredServer(serverName);
        if (registeredServer == null) {
            logger.debug("Server '{}' has no RegisteredServer, cannot cache MOTD for key '{}'", 
                    serverName, cacheKey);
            return false;
        }
        
        try {
            // Ping the server to get MOTD
            CompletableFuture<ServerPing> pingFuture = registeredServer.ping();
            ServerPing ping = pingFuture.orTimeout(5, TimeUnit.SECONDS).join();
            
            // Extract MOTD
            Component description = ping.getDescriptionComponent();
            String motd = "";
            
            if (description != null) {
                motd = MiniMessageUtil.serialize(description);
            }
            
            // Cache the MOTD
            motdCache.put(cacheKey, motd);
            
            logger.debug("Cached MOTD for key '{}' (server: '{}') before stop: motd='{}'", 
                    cacheKey, serverName, motd);
            
            // Save cache to file asynchronously
            saveCacheAsync();
            
            return true;
        } catch (Exception e) {
            logger.warn("Failed to cache MOTD for key '{}' (server: '{}') before stop: {}", 
                    cacheKey, serverName, e.getMessage());
            logger.debug("MOTD cache error details:", e);
            return false;
        }
    }
    
    /**
     * Caches MOTD for all servers with virtual_host configured.
     */
    private void cacheMotds() {
        logger.debug("Caching MOTDs for all configured virtual hosts...");
        
        PluginConfig config = serverManager.getPluginConfig();
        if (config == null || config.getServers() == null) {
            logger.debug("No servers configured, skipping MOTD cache");
            return;
        }
        
        int cached = 0;
        int failed = 0;
        
        for (Map.Entry<String, ServerConfig> entry : config.getServers().entrySet()) {
            String serverName = entry.getKey();
            ServerConfig serverConfig = entry.getValue();
            String virtualHost = serverConfig.getVirtualHost();
            
            // Use "default" if no virtual host is provided
            String cacheKey = (virtualHost == null || virtualHost.isEmpty()) ? "default" : virtualHost;
            
            RegisteredServer registeredServer = serverManager.getRegisteredServer(serverName);
            if (registeredServer == null) {
                logger.debug("Server '{}' has no RegisteredServer, skipping MOTD cache for key '{}'", 
                        serverName, cacheKey);
                failed++;
                continue;
            }
            
            try {
                // Ping the server to get MOTD
                CompletableFuture<ServerPing> pingFuture = registeredServer.ping();
                ServerPing ping = pingFuture.orTimeout(5, TimeUnit.SECONDS).join();
                
                // Extract MOTD
                Component description = ping.getDescriptionComponent();
                String motd = "";
                
                if (description != null) {
                    motd = MiniMessageUtil.serialize(description);
                }
                
                // Cache the MOTD
                motdCache.put(cacheKey, motd);
                cached++;
                
                logger.debug("Cached MOTD for key '{}' (server: '{}'): motd='{}'", 
                        cacheKey, serverName, motd);
                
            } catch (Exception e) {
                logger.warn("Failed to cache MOTD for key '{}' (server: '{}'): {}", 
                        cacheKey, serverName, e.getMessage());
                logger.debug("MOTD cache error details:", e);
                failed++;
            }
        }
        
        logger.debug("MOTD cache completed: {} cached, {} failed", cached, failed);
        
        // Save cache to file asynchronously
        saveCacheAsync();
    }
    
    /**
     * Loads cached MOTD from file.
     */
    private void loadCache() {
        synchronized (fileLock) {
            if (!Files.exists(cacheFile)) {
                logger.debug("MOTD cache file does not exist: {}", cacheFile);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(cacheFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // Format: virtualHost:motd (newlines replaced with <br>)
                    int colonIndex = line.indexOf(':');
                    if (colonIndex <= 0) {
                        continue;
                    }
                    
                    String virtualHost = line.substring(0, colonIndex);
                    String motd = line.substring(colonIndex + 1);
                    
                    if (!motd.isEmpty()) {
                        motdCache.put(virtualHost, motd);
                        logger.debug("Loaded MOTD for virtual host '{}'", virtualHost);
                    }
                }
                
                logger.debug("Loaded MOTD cache data for {} virtual hosts", motdCache.size());
                
            } catch (IOException e) {
                logger.warn("Failed to load MOTD cache data: {}", e.getMessage());
                logger.debug("MOTD cache load error details:", e);
            }
        }
    }
    
    /**
     * Saves cached MOTD to file.
     */
    private void saveCache() {
        synchronized (fileLock) {
            try {
                // Ensure parent directory exists
                Files.createDirectories(cacheFile.getParent());
                
                try (BufferedWriter writer = Files.newBufferedWriter(cacheFile)) {
                    writer.write("# AutoStartStop MOTD cache data\n");
                    writer.write("# Format: virtualHost:motd\n");
                    writer.write("# MOTD is stored in MiniMessage format, newlines replaced with <br> to handle multiline content\n\n");
                    
                    for (Map.Entry<String, String> entry : motdCache.entrySet()) {
                        String virtualHost = entry.getKey();
                        String motd = entry.getValue();
                        
                        if (motd != null && !motd.isEmpty()) {
                            // Replace newlines with <br> to handle multiline MOTD in single line format
                            String encodedMotd = motd.replace("\n", "<br>");
                            
                            writer.write(virtualHost);
                            writer.write(":");
                            writer.write(encodedMotd);
                            writer.write("\n");
                        }
                    }
                }
                
                logger.debug("Saved MOTD cache data for {} virtual hosts", motdCache.size());
                
            } catch (IOException e) {
                logger.warn("Failed to save MOTD cache data: {}", e.getMessage());
                logger.debug("MOTD cache save error details:", e);
            }
        }
    }
}
