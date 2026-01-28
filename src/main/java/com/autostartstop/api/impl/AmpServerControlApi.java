package com.autostartstop.api.impl;

import com.autostartstop.Log;
import com.autostartstop.api.ServerControlApi;
import com.autostartstop.config.ControlApiConfig;
import com.autostartstop.server.ServerState;
import com.autostartstop.util.DurationUtil;
import dev.neuralnexus.ampapi.auth.AuthProvider;
import dev.neuralnexus.ampapi.auth.RefreshingAuthProvider;
import dev.neuralnexus.ampapi.modules.ADS;
import dev.neuralnexus.ampapi.modules.CommonAPI;
import dev.neuralnexus.ampapi.types.ApplicationState;
import dev.neuralnexus.ampapi.types.IADSInstance;
import dev.neuralnexus.ampapi.types.InstanceSummary;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AMP Panel-based implementation of ServerControlApi using ADS (Application Deployment System).
 * Connects to ADS, finds the instance by ID, and controls that specific server.
 */
public class AmpServerControlApi implements ServerControlApi {
    private static final Logger logger = Log.get(AmpServerControlApi.class);
    private static final String TYPE = "amp";
    private static final long DEFAULT_INSTANCE_START_TIMEOUT_MS = 30000; // 30 seconds

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Dedicated executor for AMP API operations.
     * Uses cached thread pool suitable for blocking I/O operations (network calls to AMP panel).
     * This ensures AMP operations don't block the main thread or Velocity console.
     */
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("amp-api-executor-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /**
     * Start settings.
     */
    public enum StartSetting {
        /** Start instance if inactive, then start the server (default). */
        INSTANCE_AND_SERVER,
        /** Start server directly (fails if instance is inactive). */
        SERVER;

        public static StartSetting fromString(String value) {
            if (value == null || value.isBlank()) {
                return INSTANCE_AND_SERVER;
            }
            return switch (value.toLowerCase()) {
                case "instance_and_server" -> INSTANCE_AND_SERVER;
                case "server" -> SERVER;
                default -> INSTANCE_AND_SERVER;
            };
        }
    }

    /**
     * Stop settings.
     */
    public enum StopSetting {
        /** Stop the instance (and therefore the server) completely. */
        INSTANCE_AND_SERVER,
        /** Just stop the server (default). */
        SERVER;

        public static StopSetting fromString(String value) {
            if (value == null || value.isBlank()) {
                return SERVER;
            }
            return switch (value.toLowerCase()) {
                case "instance_and_server" -> INSTANCE_AND_SERVER;
                case "server" -> SERVER;
                default -> SERVER;
            };
        }
    }

    private final String serverName;
    private final String adsUrl;
    private final String username;
    private final String password;
    private final String token;
    private final boolean rememberMe;
    private final String instanceId;
    private final StartSetting startSetting;
    private final StopSetting stopSetting;
    private final long instanceStartTimeoutMs;

    /**
     * Lock object for synchronizing authentication and connection operations.
     */
    private final Object connectionLock = new Object();

    private volatile CommonAPI instanceApi;
    private volatile AuthProvider adsAuthProvider;
    private volatile AuthProvider instanceAuthProvider;
    private volatile IADSInstance targetInstance;
    private volatile UUID instanceUuid;
    private volatile ADS adsApi;
    private volatile boolean initialized = false;

    /**
     * Creates an AmpServerControlApi from the given configuration.
     */
    public static AmpServerControlApi create(ControlApiConfig config, String serverName) {
        // Parse instance start timeout
        long instanceStartTimeoutMs = DEFAULT_INSTANCE_START_TIMEOUT_MS;
        String timeoutStr = config.getInstanceStartTimeout();
        if (timeoutStr != null && !timeoutStr.isBlank()) {
            try {
                instanceStartTimeoutMs = DurationUtil.parse(timeoutStr).toMillis();
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid instance_start_timeout '{}' for server '{}', using default {}ms",
                        timeoutStr, serverName, DEFAULT_INSTANCE_START_TIMEOUT_MS);
            }
        }

        // Parse start and stop settings
        StartSetting startSetting = StartSetting.fromString(config.getStart());
        StopSetting stopSetting = StopSetting.fromString(config.getStop());
        
        logger.debug("Server '{}': parsed start_mode='{}' (from config: '{}'), stop_mode='{}' (from config: '{}')",
                serverName, startSetting, config.getStart(), stopSetting, config.getStop());

        return new AmpServerControlApi(
                serverName,
                config.getAdsUrl(),
                config.getUsername(),
                config.getPassword(),
                config.getInstanceId(),
                config.getToken(),
                config.isRememberMe(),
                startSetting,
                stopSetting,
                instanceStartTimeoutMs
        );
    }

    /**
     * Creates an AmpServerControlApi instance for a specific server.
     *
     * @param serverName              the name of the server this instance controls
     * @param adsUrl                  the ADS URL (e.g., "http://localhost:8080/")
     * @param username                the AMP panel username
     * @param password                the AMP panel password
     * @param instanceId              the instance ID to control
     * @param token                   the token for 2FA or remember me (optional, can be empty string)
     * @param rememberMe              whether to use remember me token
     * @param startSetting            the start setting to use
     * @param stopSetting             the stop setting to use
     * @param instanceStartTimeoutMs  timeout in milliseconds to wait for instance to become login-ready (default: 30000ms = 30s)
     */
    public AmpServerControlApi(String serverName, String adsUrl,
                               String username, String password, String instanceId,
                               String token, boolean rememberMe,
                               StartSetting startSetting, StopSetting stopSetting,
                               long instanceStartTimeoutMs) {
        this.serverName = serverName;
        this.adsUrl = adsUrl;
        this.username = username;
        this.password = password;
        this.instanceId = instanceId;
        this.token = token != null ? token : "";
        this.rememberMe = rememberMe;
        this.startSetting = startSetting != null ? startSetting : StartSetting.INSTANCE_AND_SERVER;
        this.stopSetting = stopSetting != null ? stopSetting : StopSetting.SERVER;
        this.instanceStartTimeoutMs = instanceStartTimeoutMs;

        // Connection initialization is lazy - will be done on first use
        logger.debug("Server '{}': AmpServerControlApi created (lazy initialization)", serverName);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean supportsPing() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> ping() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Pinging server '{}' (instance: {}) via AMP ADS", serverName, instanceId);

                // Ensure connection is initialized (lazy)
                ensureInitialized();

                // Ensure we're authenticated with ADS
                ensureAdsAuthenticated();

                // Handle start setting - if instance_and_server, ensure instance is ready first
                if (startSetting == StartSetting.INSTANCE_AND_SERVER) {
                    // Try to authenticate with instance, if it fails the server is not ready
                    try {
                        ensureInstanceAuthenticated();
                    } catch (Exception e) {
                        logger.debug("Server '{}': instance not ready for login, considering offline: {}", 
                                serverName, e.getMessage());
                        return false;
                    }
                } else {
                    // Try to authenticate with instance
                    try {
                        ensureInstanceAuthenticated();
                    } catch (Exception e) {
                        logger.debug("Server '{}': instance authentication failed, considering offline: {}", 
                                serverName, e.getMessage());
                        return false;
                    }
                }

                // Get the server status
                var statusResult = instanceApi.Core.GetStatus();
                if (statusResult.isError()) {
                    statusResult.peekError(err -> logger.debug("Server '{}': failed to get status: {}", 
                            serverName, err));
                    return false;
                }

                var status = statusResult.discardError().orElse(null);
                if (status == null) {
                    logger.debug("Server '{}': status is null, considering offline", serverName);
                    return false;
                }

                ApplicationState state = status.State();
                boolean isOnline = state == ApplicationState.Ready;
                logger.debug("Server '{}': AMP status = {} (online: {})", serverName, state, isOnline);
                return isOnline;
            } catch (Exception e) {
                logger.debug("Server '{}': ping failed with exception: {}", serverName, e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Ensures that the connection has been initialized.
     * Thread-safe: uses synchronized block to prevent concurrent initialization.
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        
        synchronized (connectionLock) {
            if (!initialized) {
                initializeConnection();
                initialized = true;
            }
        }
    }

    /**
     * Initializes the connection to ADS and finds the target instance.
     */
    private void initializeConnection() {
        logger.debug("Server '{}': initializing AMP ADS connection to {}", serverName, adsUrl);
        try {
            // Build ADS auth provider
            logger.debug("Server '{}': building ADS auth provider (user: {}, rememberMe: {})", 
                    serverName, username, rememberMe);
            this.adsAuthProvider = RefreshingAuthProvider.builder()
                    .relogInterval(30 * 1000)
                    .panelUrl(adsUrl)
                    .username(username)
                    .password(password)
                    .token(token)
                    .rememberMe(rememberMe)
                    .build();

            // Create ADS API
            logger.debug("Server '{}': creating ADS API instance", serverName);
            this.adsApi = new ADS(adsAuthProvider);

            // Ensure authenticated
            logger.debug("Server '{}': authenticating with ADS...", serverName);
            ensureAdsAuthenticated();
            logger.debug("Server '{}': ADS authentication successful", serverName);

            // Get available instances
            logger.debug("Server '{}': fetching available instances from ADS...", serverName);
            var instancesResult = adsApi.ADSModule.GetInstances(false);
            if (instancesResult.isError()) {
                instancesResult.peekError(err -> logger.error("Server '{}': failed to get instances from ADS: {}", serverName, err));
                throw new RuntimeException("Failed to get instances from ADS");
            }
            List<IADSInstance> targets = instancesResult.discardError().orElse(List.of());
            logger.debug("Server '{}': found {} ADS targets", serverName, targets.size());

            // Find the instance by ID
            this.targetInstance = null;
            this.instanceUuid = null;

            // Try to parse instanceId as UUID first
            UUID parsedUuid = null;
            try {
                parsedUuid = UUID.fromString(instanceId);
                logger.debug("Server '{}': instance ID '{}' is a valid UUID", serverName, instanceId);
            } catch (IllegalArgumentException e) {
                logger.debug("Server '{}': instance ID '{}' is not a UUID, searching by name", serverName, instanceId);
            }

            int totalInstances = 0;
            for (IADSInstance target : targets) {
                List<InstanceSummary> instances = target.AvailableInstances();
                totalInstances += instances.size();
                logger.debug("Server '{}': checking {} instances on target", serverName, instances.size());
                
                for (InstanceSummary instance : instances) {
                    if (parsedUuid != null && instance.InstanceID().equals(parsedUuid)) {
                        this.targetInstance = target;
                        this.instanceUuid = instance.InstanceID();
                        logger.debug("Server '{}': found instance by UUID '{}' (name: {})", 
                                serverName, instanceId, instance.InstanceName());
                        break;
                    } else if (parsedUuid == null && instance.InstanceName().equals(instanceId)) {
                        this.targetInstance = target;
                        this.instanceUuid = instance.InstanceID();
                        logger.debug("Server '{}': found instance by name '{}' (UUID: {})", 
                                serverName, instanceId, instanceUuid);
                        break;
                    }
                }
                if (this.targetInstance != null) {
                    break;
                }
            }

            if (this.targetInstance == null || this.instanceUuid == null) {
                logger.error("Server '{}': instance '{}' not found in ADS (searched {} instances across {} targets)", 
                        serverName, instanceId, totalInstances, targets.size());
                throw new IllegalStateException("Instance with ID/name '" + instanceId + "' not found in ADS");
            }

            // Create auth provider for the specific instance
            String instanceUrl = adsAuthProvider.dataSource() + "ADSModule/Servers/" + this.instanceUuid;
            logger.debug("Server '{}': creating instance auth provider for URL: {}", serverName, instanceUrl);
            this.instanceAuthProvider = RefreshingAuthProvider.builder()
                    .relogInterval(30 * 1000)
                    .panelUrl(instanceUrl)
                    .username(username)
                    .password(password)
                    .token(token)
                    .rememberMe(rememberMe)
                    .build();

            // Create API for the instance
            this.instanceApi = new CommonAPI(instanceAuthProvider);

            logger.debug("Server '{}': AMP ADS connection initialized (instance: {}, UUID: {})",
                    serverName, instanceId, instanceUuid);

        } catch (Exception e) {
            String errorMessage = e.getMessage();
            logger.error("Server '{}': failed to initialize AMP ADS connection: {}", serverName, errorMessage);
            logger.debug("Server '{}': ADS connection error details:", serverName, e);
            throw new RuntimeException("AMP initialization failed: " + errorMessage, e);
        }
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Starting server '{}' (instance: {}) via AMP ADS (setting: {})",
                        serverName, instanceId, startSetting);

                // Ensure connection is initialized (lazy)
                ensureInitialized();

                // Ensure we're authenticated
                ensureAdsAuthenticated();

                // Handle start setting
                if (startSetting == StartSetting.INSTANCE_AND_SERVER) {
                    logger.debug("Ensuring instance '{}' is started before starting server", instanceId);
                    startInstance(); // safe if already running
                    waitForInstanceLoginReady(instanceStartTimeoutMs);
                }

                // For StartSetting.SERVER, we intentionally *do not* start the instance here.
                // If the instance is inactive, authentication / server start will fail.

                // Now authenticate with the instance and start the server
                ensureInstanceAuthenticated();

                // Call the Start method
                var result = instanceApi.Core.Start();
                if (result.isError()) {
                    result.peekError(err -> logger.error("Failed to start server '{}' (instance: {}) via AMP ADS: {}",
                            serverName, instanceId, err));
                    return false;
                }

                logger.debug("AMP: server '{}' start command succeeded", serverName);
                return true;
            } catch (Exception e) {
                logger.error("Error while starting server '{}' (instance: {}) via AMP ADS: {}", 
                        serverName, instanceId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> stop() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Stopping server '{}' (instance: {}) via AMP ADS (setting: {})",
                        serverName, instanceId, stopSetting);

                // Ensure connection is initialized (lazy)
                ensureInitialized();

                // Ensure we're authenticated
                ensureAdsAuthenticated();

                if (stopSetting == StopSetting.INSTANCE_AND_SERVER) {
                    logger.debug("Stopping instance '{}' completely", instanceId);
                    stopInstance();
                } else {
                    // Just stop the server
                    ensureInstanceAuthenticated();
                    var result = instanceApi.Core.Stop();
                    if (result.isError()) {
                        result.peekError(err -> logger.error("Failed to stop server '{}' (instance: {}) via AMP ADS: {}",
                                serverName, instanceId, err));
                        return false;
                    }
                    logger.debug("AMP: server '{}' stop command succeeded", serverName);
                }
                return true;
            } catch (Exception e) {
                logger.error("Error while stopping server '{}' (instance: {}) via AMP ADS: {}", 
                        serverName, instanceId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> restart() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Restarting server '{}' (instance: {}) via AMP ADS (setting: {})",
                        serverName, instanceId, startSetting);

                // Ensure connection is initialized (lazy)
                ensureInitialized();

                // Ensure we're authenticated with ADS
                ensureAdsAuthenticated();

                // Handle start setting - if instance_and_server, ensure instance is fully ready
                if (startSetting == StartSetting.INSTANCE_AND_SERVER) {
                    logger.debug("Ensuring instance '{}' is started before restarting server", instanceId);
                    startInstance(); // safe if already running
                    waitForInstanceLoginReady(instanceStartTimeoutMs);
                }

                // Now authenticate with the instance
                ensureInstanceAuthenticated();

                // Check if server is actually running before deciding to restart or start
                var statusResult = instanceApi.Core.GetStatus();
                if (statusResult.isError()) {
                    statusResult.peekError(err -> logger.warn("Failed to get status for server '{}' (instance: {}): {}, attempting restart anyway",
                            serverName, instanceId, err));
                } else {
                    var status = statusResult.discardError().orElse(null);
                    if (status != null) {
                        ApplicationState state = status.State();
                        logger.debug("Server '{}' current state: {}", serverName, state);
                        // ApplicationState.Ready = Running, other states like Stopped, Starting, etc.
                        // If server is not running, start it instead of restarting
                        if (state != ApplicationState.Ready) {
                            logger.debug("Server '{}' is not running (state: {}), starting instead of restarting", serverName, state);
                            var startResult = instanceApi.Core.Start();
                            if (startResult.isError()) {
                                startResult.peekError(err -> logger.error("Failed to start server '{}' (instance: {}) via AMP ADS: {}",
                                        serverName, instanceId, err));
                                return false;
                            }
                            logger.debug("AMP: server '{}' start command succeeded (was not running)", serverName);
                            return true;
                        }
                    }
                }

                // Server is running, call the Restart method
                var result = instanceApi.Core.Restart();
                if (result.isError()) {
                    result.peekError(err -> logger.error("Failed to restart server '{}' (instance: {}) via AMP ADS: {}",
                            serverName, instanceId, err));
                    return false;
                }

                logger.debug("AMP: server '{}' restart command succeeded", serverName);
                return true;
            } catch (Exception e) {
                logger.error("Error while restarting server '{}' (instance: {}) via AMP ADS: {}", 
                        serverName, instanceId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    /**
     * Ensures that we're authenticated with the ADS panel.
     * Thread-safe: uses synchronized block to prevent concurrent login attempts.
     */
    private void ensureAdsAuthenticated() {
        // Fast path: check without lock first
        if (!adsAuthProvider.sessionId().isEmpty()) {
            return;
        }
        
        synchronized (connectionLock) {
            // Double-check after acquiring lock
            if (adsAuthProvider.sessionId().isEmpty()) {
                logger.debug("Not authenticated with ADS, attempting to login for server '{}'", serverName);
                try {
                    adsAuthProvider.Login();
                    if (adsAuthProvider.sessionId().isEmpty()) {
                        logger.error("Server '{}': ADS login returned empty session (invalid credentials?)", serverName);
                        throw new IllegalStateException("ADS login failed - empty session (invalid credentials?)");
                    }
                    logger.debug("Successfully authenticated with ADS for server '{}'", serverName);
                } catch (IllegalStateException e) {
                    // Re-throw our own exceptions
                    throw e;
                } catch (Exception e) {
                    logger.error("Server '{}': ADS connection/login error: {}", serverName, e.getMessage());
                    throw new IllegalStateException("ADS connection/login error: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Ensures that we're authenticated with the instance API.
     * Thread-safe: uses synchronized block to prevent concurrent login attempts.
     */
    private void ensureInstanceAuthenticated() {
        // Ensure connection is initialized first
        ensureInitialized();
        
        // Fast path: check without lock first
        AuthProvider localInstanceAuth = instanceAuthProvider;
        if (localInstanceAuth != null && !localInstanceAuth.sessionId().isEmpty()) {
            return;
        }
        
        synchronized (connectionLock) {
            // Double-check after acquiring lock
            if (instanceAuthProvider == null || instanceAuthProvider.sessionId().isEmpty()) {
                logger.debug("Not authenticated with instance, attempting to login for server '{}' (instance: {})", serverName, instanceId);
                try {
                    instanceAuthProvider.Login();
                    if (instanceAuthProvider.sessionId().isEmpty()) {
                        logger.error("Server '{}': instance '{}' login returned empty session (invalid credentials?)", serverName, instanceId);
                        throw new IllegalStateException("Instance login failed - empty session (invalid credentials?)");
                    }
                    logger.debug("Successfully authenticated with instance '{}' for server '{}'", instanceId, serverName);
                } catch (IllegalStateException e) {
                    // Re-throw our own exceptions
                    throw e;
                } catch (Exception e) {
                    logger.error("Server '{}': instance '{}' connection/login error: {}", serverName, instanceId, e.getMessage());
                    throw new IllegalStateException("Instance connection/login error: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Starts the instance.
     */
    private void startInstance() {
        try {
            ensureAdsAuthenticated();
            logger.debug("Starting instance '{}' via ADS", instanceId);

            // Use ADS API to start the instance - convert UUID to string
            var result = adsApi.ADSModule.StartInstance(instanceUuid.toString());
            if (result.isError()) {
                result.peekError(err -> logger.error("Failed to start instance '{}' via ADS: {}", instanceId, err));
                throw new RuntimeException("Failed to start instance via ADS");
            }
            logger.debug("Instance '{}' start command succeeded", instanceId);
        } catch (Exception e) {
            logger.error("Error starting instance '{}': {}", instanceId, e.getMessage(), e);
            throw new RuntimeException("Failed to start instance: " + e.getMessage(), e);
        }
    }

    /**
     * Stops the instance completely.
     */
    private void stopInstance() {
        try {
            ensureAdsAuthenticated();
            logger.debug("Stopping instance '{}' completely via ADS", instanceId);

            // Use ADS API to stop the instance - convert UUID to string
            var result = adsApi.ADSModule.StopInstance(instanceUuid.toString());
            if (result.isError()) {
                result.peekError(err -> logger.error("Failed to stop instance '{}' via ADS: {}", instanceId, err));
                throw new RuntimeException("Failed to stop instance via ADS");
            }
            logger.debug("Instance '{}' stop command succeeded", instanceId);
        } catch (Exception e) {
            logger.error("Error stopping instance '{}': {}", instanceId, e.getMessage(), e);
            throw new RuntimeException("Failed to stop instance: " + e.getMessage(), e);
        }
    }

    /**
     * Waits for the instance API to be ready to accept logins.
     * Thread-safe: uses synchronized block for login attempts.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     */
    private void waitForInstanceLoginReady(long timeoutMs) {
        logger.debug("Server '{}': waiting for instance '{}' to become login-ready (timeout: {}ms)", 
                serverName, instanceId, timeoutMs);
        long startTime = System.currentTimeMillis();
        int attempts = 0;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            attempts++;
            try {
                synchronized (connectionLock) {
                    AuthProvider localInstanceAuth = instanceAuthProvider;
                    if (localInstanceAuth != null) {
                        localInstanceAuth.Login();
                        if (!localInstanceAuth.sessionId().isEmpty()) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            logger.debug("Server '{}': instance '{}' is now accepting logins (took {}ms, {} attempts)", 
                                    serverName, instanceId, elapsed, attempts);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                // Instance may still be starting up, retry
                if (attempts % 10 == 0) { // Log every 5 seconds (10 * 500ms)
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.debug("Server '{}': instance '{}' not ready yet ({}ms elapsed, {} attempts): {}", 
                            serverName, instanceId, elapsed, attempts, e.getMessage());
                }
            }

            try {
                Thread.sleep(500); // Check every 500ms
            } catch (InterruptedException e) {
                logger.warn("Server '{}': interrupted while waiting for instance login readiness", serverName);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for instance login readiness", e);
            }
        }
        logger.warn("Server '{}': instance '{}' did not become login-ready within {}ms after {} attempts - proceeding anyway", 
                serverName, instanceId, timeoutMs, attempts);
    }

    public String getServerName() {
        return serverName;
    }

    public String getAdsUrl() {
        return adsUrl;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public StartSetting getStartSetting() {
        return startSetting;
    }

    public StopSetting getStopSetting() {
        return stopSetting;
    }

    @Override
    public boolean supportsState() {
        return true;
    }

    @Override
    public boolean supportsCommandSending() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> sendCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (command == null || command.isBlank()) {
                    logger.warn("Server '{}': cannot send empty command", serverName);
                    return false;
                }

                logger.debug("Sending command to server '{}' (instance: {}) via AMP ADS: {}", 
                        serverName, instanceId, command);

                // Ensure connection is initialized (lazy)
                ensureInitialized();

                // Ensure we're authenticated
                ensureAdsAuthenticated();

                // Authenticate with instance
                ensureInstanceAuthenticated();

                // Send console message
                var result = instanceApi.Core.SendConsoleMessage(command);
                if (result.isError()) {
                    result.peekError(err -> logger.error("Failed to send command to server '{}' (instance: {}) via AMP ADS: {}",
                            serverName, instanceId, err));
                    return false;
                }

                logger.debug("AMP: command sent to server '{}' successfully", serverName);
                return true;
            } catch (Exception e) {
                logger.error("Error while sending command to server '{}' (instance: {}) via AMP ADS: {}", 
                        serverName, instanceId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    public ServerState getState() {
        try {
            // Ensure connection is initialized (lazy)
            ensureInitialized();

            // Ensure we're authenticated with ADS
            ensureAdsAuthenticated();

            // Try to authenticate with instance
            try {
                ensureInstanceAuthenticated();
            } catch (Exception e) {
                logger.debug("Server '{}': instance not ready for login, state is 'Stopped'", serverName);
                return normalizeAmpState(ApplicationState.Stopped);
            }

            // Get the server status
            var statusResult = instanceApi.Core.GetStatus();
            if (statusResult.isError()) {
                statusResult.peekError(err -> logger.debug("Server '{}': failed to get status: {}", 
                        serverName, err));
                return ServerState.UNKNOWN;
            }

            var status = statusResult.discardError().orElse(null);
            if (status == null) {
                logger.debug("Server '{}': status is null", serverName);
                return ServerState.UNKNOWN;
            }

            ApplicationState state = status.State();
            logger.debug("Server '{}': AMP state = {}", serverName, state);
            return normalizeAmpState(state);
        } catch (Exception e) {
            logger.debug("Server '{}': getState failed with exception: {}", serverName, e.getMessage());
            return ServerState.UNKNOWN;
        }
    }

    /**
     * Normalizes AMP ApplicationState to our standardized ServerState.
     * 
     * @param ampState The AMP ApplicationState enum value
     * @return Normalized ServerState enum value
     */
    private ServerState normalizeAmpState(ApplicationState ampState) {
        if (ampState == null) {
            return ServerState.UNKNOWN;
        }
        
        // Map AMP ApplicationState enum values to normalized states
        return switch (ampState) { 
            case Ready -> ServerState.ONLINE;
            case Starting, PreStart, Configuring -> ServerState.STARTING;
            case Stopping -> ServerState.STOPPING;
            case Stopped -> ServerState.OFFLINE;
            case Restarting -> ServerState.RESTARTING;
            case Failed -> ServerState.FAILED;
            default -> ServerState.UNKNOWN;
        };
    }

    /**
     * Shuts down the AMP API executor.
     * Should be called during plugin shutdown.
     */
    public static void shutdown() {
        logger.debug("Shutting down AMP API executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.debug("AMP API executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.debug("AMP API executor shut down");
    }
}
