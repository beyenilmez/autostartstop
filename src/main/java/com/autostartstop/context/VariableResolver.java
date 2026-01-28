package com.autostartstop.context;

import com.autostartstop.Log;
import com.autostartstop.server.ManagedServer;
import com.autostartstop.server.ServerManager;
import com.autostartstop.server.ServerState;
import com.autostartstop.server.ServerStartupTracker;
import com.autostartstop.util.DurationUtil;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;


/**
 * Resolves ${...} variable placeholders and provides type-safe parsing.
 * 
 * <p>Supports:
 * <ul>
 *   <li>Context variables set during rule execution</li>
 *   <li>Global server variables: ${server-name.status}, ${server-name.player_count}, etc.</li>
 *   <li>Type-safe resolution: resolveDuration(), resolveInt(), resolveEnum(), etc.</li>
 * </ul>
 */
public class VariableResolver {
    private static final Logger logger = Log.get(VariableResolver.class);

    private ServerManager serverManager;
    private ServerStartupTracker serverStartupTracker;

    /**
     * Extracts the variable name from ${...} syntax.
     * Returns the input unchanged if not wrapped in ${}.
     */
    public static String extractVariableName(String input) {
        if (input == null) {
            return null;
        }
        if (input.startsWith("${") && input.endsWith("}")) {
            return input.substring(2, input.length() - 1);
        }
        return input;
    }

    public void setServerManager(ServerManager serverManager) {
        this.serverManager = serverManager;
        logger.debug("ServerManager injected");
    }

    public void setServerStartupTracker(ServerStartupTracker tracker) {
        this.serverStartupTracker = tracker;
        logger.debug("ServerStartupTracker injected");
    }

    // ========== String Resolution ==========

    /**
     * Maximum number of resolution passes to prevent infinite loops.
     */
    private static final int MAX_RESOLUTION_PASSES = 5;

    /**
     * Resolves all ${...} placeholders in the input string.
     * Supports nested variables like ${${connection.server.name}.property}.
     * Unresolved placeholders are kept as-is.
     */
    public String resolve(String input, ExecutionContext context) {
        if (input == null || !input.contains("${")) {
            return input;
        }

        String current = input;
        
        // Keep resolving until no more changes (handles nested variables)
        for (int pass = 0; pass < MAX_RESOLUTION_PASSES; pass++) {
            String resolved = resolveOnePass(current, context);
            if (resolved.equals(current)) {
                // No changes made, we're done
                break;
            }
            current = resolved;
            
            // Early exit if no more variables to resolve
            if (!current.contains("${")) {
                break;
            }
        }

        return current;
    }

    /**
     * Performs a single pass of variable resolution.
     * Resolves innermost variables first to support nesting.
     */
    private String resolveOnePass(String input, ExecutionContext context) {
        if (input == null || !input.contains("${")) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < input.length()) {
            if (i < input.length() - 1 && input.charAt(i) == '$' && input.charAt(i + 1) == '{') {
                // Find the matching closing brace, accounting for nested ${
                int start = i;
                int braceCount = 0;
                int j = i;
                
                while (j < input.length()) {
                    if (j < input.length() - 1 && input.charAt(j) == '$' && input.charAt(j + 1) == '{') {
                        braceCount++;
                        j += 2;
                    } else if (input.charAt(j) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            // Found matching brace
                            String fullMatch = input.substring(start, j + 1);
                            String variableExpr = input.substring(start + 2, j);
                            
                            // If the variable expression contains nested ${, resolve those first
                            if (variableExpr.contains("${")) {
                                String resolvedExpr = resolveOnePass(variableExpr, context);
                                // Try to resolve the now-flattened variable
                                Object value = resolveVariable(resolvedExpr, context);
                                if (value != null) {
                                    result.append(value.toString());
                                } else {
                                    // Keep as variable for next pass (with resolved inner part)
                                    result.append("${").append(resolvedExpr).append("}");
                                }
                            } else {
                                // Simple variable, resolve directly
                                Object value = resolveVariable(variableExpr, context);
                                if (value != null) {
                                    result.append(value.toString());
                                } else {
                                    // Keep original if unresolved
                                    result.append(fullMatch);
                                }
                            }
                            i = j + 1;
                            break;
                        }
                        j++;
                    } else {
                        j++;
                    }
                }
                
                // If we didn't find a matching brace, append as-is
                if (braceCount != 0) {
                    result.append(input.charAt(i));
                    i++;
                }
            } else {
                result.append(input.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Resolves a single variable by name.
     * Checks context first, then global server variables.
     */
    public Object resolveVariable(String variableName, ExecutionContext context) {
        Object value = context.getVariable(variableName);
        if (value != null) {
            return value;
        }
        return resolveGlobalServerVariable(variableName);
    }

    // ========== Type-Safe Resolution ==========

    /**
     * Resolves and parses a duration (e.g., "1s", "500ms", "2m").
     */
    public Duration resolveDuration(String raw, ExecutionContext context, Duration defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        
        String resolved = resolve(raw, context);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        
        try {
            return DurationUtil.parse(resolved);
        } catch (Exception e) {
            logger.debug("Failed to parse duration '{}': {}", resolved, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Resolves and parses an integer.
     */
    public int resolveInt(String raw, ExecutionContext context, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        
        String resolved = resolve(raw, context);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(resolved.trim());
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse integer '{}': {}", resolved, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Resolves an integer from an Object (handles String and Number).
     */
    public int resolveInt(Object raw, ExecutionContext context, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        return resolveInt(raw.toString(), context, defaultValue);
    }

    /**
     * Resolves and parses a long.
     */
    public long resolveLong(String raw, ExecutionContext context, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        
        String resolved = resolve(raw, context);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        
        try {
            return Long.parseLong(resolved.trim());
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse long '{}': {}", resolved, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Resolves and parses a double.
     */
    public double resolveDouble(String raw, ExecutionContext context, double defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        
        String resolved = resolve(raw, context);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(resolved.trim());
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse double '{}': {}", resolved, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Resolves a double from an Object (handles String and Number).
     */
    public double resolveDouble(Object raw, ExecutionContext context, double defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        return resolveDouble(raw.toString(), context, defaultValue);
    }

    /**
     * Resolves a float clamped to [min, max].
     */
    public float resolveFloatClamped(String raw, ExecutionContext context, float defaultValue, float min, float max) {
        float value = (float) resolveDouble(raw, context, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Resolves a float from an Object, clamped to [min, max].
     */
    public float resolveFloatClamped(Object raw, ExecutionContext context, float defaultValue, float min, float max) {
        float value = (float) resolveDouble(raw, context, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Resolves and parses a boolean.
     * Accepts: true/false, yes/no, 1/0, on/off.
     */
    public boolean resolveBoolean(String raw, ExecutionContext context, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        
        String resolved = resolve(raw, context);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        
        String lower = resolved.trim().toLowerCase();
        if ("true".equals(lower) || "yes".equals(lower) || "1".equals(lower) || "on".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "0".equals(lower) || "off".equals(lower)) {
            return false;
        }
        
        return defaultValue;
    }

    /**
     * Resolves a boolean from an Object.
     */
    public boolean resolveBoolean(Object raw, ExecutionContext context, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return resolveBoolean(raw.toString(), context, defaultValue);
    }

    /**
     * Resolves and parses an enum value.
     * Case-insensitive, converts hyphens to underscores.
     */
    public <T extends Enum<T>> T resolveEnum(String raw, ExecutionContext context, Class<T> enumClass, T defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        
        String resolved = resolve(raw, context);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        
        try {
            return Enum.valueOf(enumClass, resolved.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            logger.debug("Failed to parse enum '{}' for {}: {}", resolved, enumClass.getSimpleName(), e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Resolves each string in a list.
     */
    public List<String> resolveList(List<String> raw, ExecutionContext context) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(s -> resolve(s, context))
                .toList();
    }

    // ========== Global Server Variables ==========

    /**
     * Resolves global server variables.
     * 
     * <p>Supported properties:
     * <ul>
     *   <li>.name - Server name</li>
     *   <li>.status - "online" or "offline"</li>
     *   <li>.state - Detailed state (offline/starting/online)</li>
     *   <li>.startup_timer - Seconds since start began</li>
     *   <li>.startup_progress - Progress 0.0-1.0 for bossbars</li>
     *   <li>.startup_progress_percentage - Progress 0-100</li>
     *   <li>.player_count - Number of players</li>
     *   <li>.players - Collection of Player objects</li>
     * </ul>
     */
    private Object resolveGlobalServerVariable(String variableName) {
        if (serverManager == null) {
            return null;
        }

        if (variableName.endsWith(".name")) {
            String serverName = variableName.substring(0, variableName.length() - ".name".length());
            if (serverManager.hasServer(serverName)) {
                return serverName;
            }
        }

        if (variableName.endsWith(".status")) {
            String serverName = variableName.substring(0, variableName.length() - ".status".length());
            if (serverManager.hasServer(serverName)) {
                return serverManager.isServerOnline(serverName) ? "online" : "offline";
            }
        }

        if (variableName.endsWith(".state")) {
            String serverName = variableName.substring(0, variableName.length() - ".state".length());
            if (serverManager.hasServer(serverName)) {
                return resolveServerState(serverName);
            }
        }

        if (variableName.endsWith(".startup_timer")) {
            String serverName = variableName.substring(0, variableName.length() - ".startup_timer".length());
            if (serverManager.hasServer(serverName)) {
                return resolveStartupTimer(serverName);
            }
        }

        if (variableName.endsWith(".startup_progress_percentage")) {
            String serverName = variableName.substring(0, variableName.length() - ".startup_progress_percentage".length());
            if (serverManager.hasServer(serverName)) {
                return resolveStartupProgressPercentage(serverName);
            }
        }

        if (variableName.endsWith(".startup_progress")) {
            String serverName = variableName.substring(0, variableName.length() - ".startup_progress".length());
            if (serverManager.hasServer(serverName)) {
                return resolveStartupProgress(serverName);
            }
        }

        if (variableName.endsWith(".player_count")) {
            String serverName = variableName.substring(0, variableName.length() - ".player_count".length());
            if (serverManager.hasServer(serverName)) {
                return serverManager.getServerPlayerCount(serverName);
            }
        }

        if (variableName.endsWith(".players")) {
            String serverName = variableName.substring(0, variableName.length() - ".players".length());
            if (serverManager.hasServer(serverName)) {
                return serverManager.getServerPlayers(serverName);
            }
        }

        if (serverManager.hasServer(variableName)) {
            ManagedServer server = serverManager.getServer(variableName);
            if (server != null) {
                return server.getRegisteredServer();
            }
        }

        return null;
    }

    private String resolveServerState(String serverName) {
        ServerState state;
        if (serverStartupTracker != null) {
            state = serverStartupTracker.getServerState(serverName);
        } else {
            state = serverManager.isServerOnline(serverName) ? ServerState.ONLINE : ServerState.OFFLINE;
        }
        return state.getName();
    }

    private long resolveStartupTimer(String serverName) {
        if (serverStartupTracker != null) {
            return serverStartupTracker.getStartupTimerSeconds(serverName);
        }
        return 0;
    }

    private int resolveStartupProgressPercentage(String serverName) {
        if (serverStartupTracker != null) {
            return serverStartupTracker.getStartupProgressPercentage(serverName);
        }
        return serverManager.isServerOnline(serverName) ? 100 : 0;
    }

    private double resolveStartupProgress(String serverName) {
        if (serverStartupTracker != null) {
            return serverStartupTracker.getStartupProgress(serverName);
        }
        return serverManager.isServerOnline(serverName) ? 1.0 : 0.0;
    }
}
