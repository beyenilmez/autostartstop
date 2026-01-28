package com.autostartstop.api.impl;

import com.autostartstop.Log;
import com.autostartstop.api.ServerControlApi;
import com.autostartstop.config.ControlApiConfig;
import com.autostartstop.server.ServerState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pterodactyl Panel-based implementation of ServerControlApi using Client API.
 * Connects to Pterodactyl panel, authenticates with API key, and controls servers.
 */
public class PterodactylServerControlApi implements ServerControlApi {
    private static final Logger logger = Log.get(PterodactylServerControlApi.class);
    private static final String TYPE = "pterodactyl";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Thread pool for async Pterodactyl API calls.
     * Keeps network I/O off the main thread.
     */
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("pterodactyl-api-executor-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /**
     * HTTP client with connection pooling and timeouts.
     */
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(executor)
            .build();

    /**
     * Gson instance for JSON parsing.
     */
    private static final Gson gson = new Gson();

    private final String serverName;
    private final String panelUrl;
    private final String apiKey;
    private final String serverId;
    private final Duration requestTimeout;

    /**
     * Creates a PterodactylServerControlApi from the given configuration.
     */
    public static PterodactylServerControlApi create(ControlApiConfig config, String serverName) {
        String panelUrl = config.getPanelUrl();
        String apiKey = config.getApiKey();
        String serverId = config.getServerId();

        if (panelUrl == null || panelUrl.isBlank()) {
            logger.error("Server '{}': panel_url is required for Pterodactyl API", serverName);
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("Server '{}': api_key is required for Pterodactyl API", serverName);
            return null;
        }
        if (serverId == null || serverId.isBlank()) {
            logger.error("Server '{}': server_id is required for Pterodactyl API", serverName);
            return null;
        }

        // Make sure URL ends with /
        if (!panelUrl.endsWith("/")) {
            panelUrl = panelUrl + "/";
        }

        return new PterodactylServerControlApi(serverName, panelUrl, apiKey, serverId, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a PterodactylServerControlApi instance for a specific server.
     *
     * @param serverName    the name of the server this instance controls
     * @param panelUrl      the Pterodactyl panel URL (e.g., "https://panel.example.com/")
     * @param apiKey        the Client API key (format: "ptlc_...")
     * @param serverId      the server UUID identifier
     * @param requestTimeout timeout for HTTP requests
     */
    public PterodactylServerControlApi(String serverName, String panelUrl, String apiKey, 
                                      String serverId, Duration requestTimeout) {
        this.serverName = serverName;
        this.panelUrl = panelUrl;
        this.apiKey = apiKey;
        this.serverId = serverId;
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_TIMEOUT;

        logger.debug("Server '{}': PterodactylServerControlApi created (panel: {}, server_id: {})", 
                serverName, panelUrl, serverId);
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
                logger.debug("Pinging server '{}' (server_id: {}) via Pterodactyl API", serverName, serverId);

                ServerState state = getState();
                boolean isOnline = state == ServerState.ONLINE;
                logger.debug("Server '{}': Pterodactyl state = {} (online: {})", serverName, state, isOnline);
                return isOnline;
            } catch (Exception e) {
                logger.debug("Server '{}': ping failed with exception: {}", serverName, e.getMessage());
                return false;
            }
        }, executor);
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

                logger.debug("Sending command to server '{}' (server_id: {}) via Pterodactyl API: {}", 
                        serverName, serverId, command);

                String url = panelUrl + "api/client/servers/" + serverId + "/command";
                
                // Build command JSON
                JsonObject commandRequest = new JsonObject();
                commandRequest.addProperty("command", command);
                String jsonBody = gson.toJson(commandRequest);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "Application/vnd.pterodactyl.v1+json")
                        .header("Content-Type", "application/json")
                        .timeout(requestTimeout)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    logger.error("Server '{}': Pterodactyl API authentication failed (invalid API key?)", serverName);
                    return false;
                }
                if (response.statusCode() == 404) {
                    logger.error("Server '{}': Server not found in Pterodactyl panel (invalid server_id?)", serverName);
                    return false;
                }

                // Command endpoint returns 204 on success
                if (response.statusCode() != 204) {
                    logger.error("Server '{}': Failed to send command via Pterodactyl API: HTTP {}", 
                            serverName, response.statusCode());
                    String errorMsg = parseErrorMessage(response.body());
                    if (errorMsg != null) {
                        logger.debug("Server '{}': Pterodactyl error: {}", serverName, errorMsg);
                    }
                    return false;
                }

                logger.debug("Pterodactyl: command sent to server '{}' successfully (HTTP {})", 
                        serverName, response.statusCode());
                return true;
            } catch (Exception e) {
                logger.error("Error while sending command to server '{}' (server_id: {}) via Pterodactyl API: {}", 
                        serverName, serverId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    public ServerState getState() {
        try {
            logger.debug("Getting state for server '{}' (server_id: {}) via Pterodactyl API", serverName, serverId);

            // Get server state from resources endpoint
            String url = panelUrl + "api/client/servers/" + serverId + "/resources";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "Application/vnd.pterodactyl.v1+json")
                    .header("Content-Type", "application/json")
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                logger.error("Server '{}': Pterodactyl API authentication failed (invalid API key?)", serverName);
                return ServerState.UNKNOWN;
            }
            if (response.statusCode() == 404) {
                logger.error("Server '{}': Server not found in Pterodactyl panel (invalid server_id?)", serverName);
                return ServerState.UNKNOWN;
            }
            if (response.statusCode() != 200) {
                logger.error("Server '{}': Failed to get server state from Pterodactyl API: HTTP {}", 
                        serverName, response.statusCode());
                return ServerState.UNKNOWN;
            }

            String responseBody = response.body();
            // State is in attributes.current_state
            String state = parseStateFromResources(responseBody);
            
            ServerState normalizedState = normalizePterodactylState(state);
            logger.debug("Server '{}': Pterodactyl state = {} (normalized: {})", serverName, state, normalizedState);
            return normalizedState;
        } catch (Exception e) {
            logger.debug("Server '{}': getState failed with exception: {}", serverName, e.getMessage());
            return ServerState.UNKNOWN;
        }
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return sendPowerSignal("start");
    }

    @Override
    public CompletableFuture<Boolean> stop() {
        return sendPowerSignal("stop");
    }

    @Override
    public CompletableFuture<Boolean> restart() {
        return sendPowerSignal("restart");
    }

    /**
     * Sends a power signal to the server.
     *
     * @param signal the power signal ("start", "stop", or "restart")
     * @return A CompletableFuture that completes with true if successful, false otherwise
     */
    private CompletableFuture<Boolean> sendPowerSignal(String signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Sending '{}' signal to server '{}' (server_id: {}) via Pterodactyl API", 
                        signal, serverName, serverId);

                String url = panelUrl + "api/client/servers/" + serverId + "/power";
                
                // Build power signal JSON
                JsonObject powerRequest = new JsonObject();
                powerRequest.addProperty("signal", signal);
                String jsonBody = gson.toJson(powerRequest);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "Application/vnd.pterodactyl.v1+json")
                        .header("Content-Type", "application/json")
                        .timeout(requestTimeout)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    logger.error("Server '{}': Pterodactyl API authentication failed (invalid API key?)", serverName);
                    return false;
                }
                if (response.statusCode() == 404) {
                    logger.error("Server '{}': Server not found in Pterodactyl panel (invalid server_id?)", serverName);
                    return false;
                }
                if (response.statusCode() == 422) {
                    // Server is probably in wrong state (e.g., already running when trying to start)
                    logger.warn("Server '{}': Cannot {} server - server may be in wrong state (HTTP 422)", 
                            serverName, signal);
                    String errorMsg = parseErrorMessage(response.body());
                    if (errorMsg != null) {
                        logger.debug("Server '{}': Pterodactyl error: {}", serverName, errorMsg);
                    }
                    return false;
                }
                // Power endpoint returns 204 on success
                if (response.statusCode() != 204) {
                    logger.error("Server '{}': Failed to {} server via Pterodactyl API: HTTP {}", 
                            serverName, signal, response.statusCode());
                    String errorMsg = parseErrorMessage(response.body());
                    if (errorMsg != null) {
                        logger.debug("Server '{}': Pterodactyl error: {}", serverName, errorMsg);
                    }
                    return false;
                }

                logger.debug("Pterodactyl: server '{}' {} command succeeded (HTTP {})", 
                        serverName, signal, response.statusCode());
                return true;
            } catch (Exception e) {
                logger.error("Error while {} server '{}' (server_id: {}) via Pterodactyl API: {}", 
                        signal, serverName, serverId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    /**
     * Extracts server state from the resources endpoint response.
     * Looks for attributes.current_state in the JSON.
     *
     * @param responseBody the JSON response body
     * @return the state string (e.g., "running", "offline", "starting", "stopping"), or null if not found
     */
    private String parseStateFromResources(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            // Response structure: {"object": "stats", "attributes": {"current_state": "...", ...}}
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            
            // Get current_state from attributes
            if (json.has("attributes")) {
                JsonObject attributes = json.getAsJsonObject("attributes");
                if (attributes.has("current_state") && !attributes.get("current_state").isJsonNull()) {
                    String state = attributes.get("current_state").getAsString();
                    if (state != null && !state.isBlank()) {
                        return state;
                    }
                }
            }
            
            logger.debug("Server '{}': current_state field is null or missing in resources response", serverName);
        } catch (JsonSyntaxException e) {
            logger.debug("Server '{}': Failed to parse JSON from resources response: {}", 
                    serverName, e.getMessage());
        } catch (Exception e) {
            logger.debug("Server '{}': Failed to parse state from resources response: {}", 
                    serverName, e.getMessage());
        }

        return null;
    }


    /**
     * Extracts error message from Pterodactyl error response.
     * Format: {"errors": [{"code": "...", "detail": "...", ...}]}
     *
     * @param responseBody the JSON response body
     * @return the error message, or null if not found
     */
    private String parseErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            
            // Check for errors array
            if (json.has("errors") && json.get("errors").isJsonArray()) {
                var errorsArray = json.getAsJsonArray("errors");
                if (errorsArray.size() > 0) {
                    var firstError = errorsArray.get(0);
                    if (firstError.isJsonObject()) {
                        JsonObject errorObj = firstError.getAsJsonObject();
                        
                        // Combine code and detail into readable message
                        StringBuilder errorMsg = new StringBuilder();
                        
                        if (errorObj.has("code") && errorObj.get("code").isJsonPrimitive()) {
                            errorMsg.append(errorObj.get("code").getAsString());
                        }
                        
                        if (errorObj.has("detail") && errorObj.get("detail").isJsonPrimitive()) {
                            if (errorMsg.length() > 0) {
                                errorMsg.append(": ");
                            }
                            errorMsg.append(errorObj.get("detail").getAsString());
                        }
                        
                        if (errorMsg.length() > 0) {
                            return errorMsg.toString();
                        }
                    } else if (firstError.isJsonPrimitive()) {
                        return firstError.getAsString();
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Server '{}': Failed to parse error JSON: {}", serverName, e.getMessage());
        } catch (Exception e) {
            logger.debug("Server '{}': Failed to parse error message: {}", serverName, e.getMessage());
        }

        return null;
    }

    /**
     * Converts Pterodactyl state string to ServerState enum.
     *
     * @param pterodactylState state from Pterodactyl API (e.g., "running", "offline", "starting")
     * @return normalized ServerState
     */
    private ServerState normalizePterodactylState(String pterodactylState) {
        if (pterodactylState == null || pterodactylState.isBlank() || pterodactylState.equals("null")) {
            return ServerState.UNKNOWN;
        }

        String stateLower = pterodactylState.toLowerCase().trim();
        return switch (stateLower) {
            case "offline" -> ServerState.OFFLINE;
            case "starting" -> ServerState.STARTING;
            case "stopping" -> ServerState.STOPPING;
            case "running" -> ServerState.ONLINE;
            default -> {
                logger.debug("Server '{}': Unknown Pterodactyl state value: '{}', returning UNKNOWN", 
                        serverName, pterodactylState);
                yield ServerState.UNKNOWN;
            }
        };
    }

    public String getServerName() {
        return serverName;
    }

    public String getPanelUrl() {
        return panelUrl;
    }

    public String getServerId() {
        return serverId;
    }

    /**
     * Shuts down the executor thread pool.
     * Call this during plugin shutdown.
     */
    public static void shutdown() {
        logger.debug("Shutting down Pterodactyl API executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.debug("Pterodactyl API executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.debug("Pterodactyl API executor shut down");
    }
}
