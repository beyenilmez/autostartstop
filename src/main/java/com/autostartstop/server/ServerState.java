package com.autostartstop.server;

/**
 * Normalized server state values.
 * All control APIs should return one of these states.
 */
public enum ServerState {
    /**
     * State cannot be determined.
     */
    UNKNOWN,
    
    /**
     * Server is not running.
     */
    OFFLINE,
    
    /**
     * Server is in the process of starting.
     */
    STARTING,

    /**
     * Server is in the process of stopping.
     */
    STOPPING,

    /**
     * Server is in the process of restarting.
     */
    RESTARTING,
    
    /**
     * Server is running and ready.
     */
    ONLINE,
    
    /**
     * Server failed to start or encountered an error.
     */
    FAILED;
    
    /**
     * Returns the lowercase string representation of this state.
     * 
     * @return The state name in lowercase (e.g., "unknown", "offline", "starting", "online", "failed")
     */
    public String getName() {
        return name().toLowerCase();
    }
}
