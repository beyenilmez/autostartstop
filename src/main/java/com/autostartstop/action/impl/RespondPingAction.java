package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.Log;
import com.autostartstop.server.MotdCacheManager;
import com.autostartstop.server.ServerManager;
import com.autostartstop.util.IconUtil;
import com.autostartstop.util.MiniMessageUtil;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Action that responds to a ping request with custom ping data.
 * This action modifies the ProxyPingEvent to customize the ping response.
 * 
 * Configuration:
 * - ping: ServerPing object to use (default: ${ping}, which means use event.getPing())
 * - use_cached_motd: Use cached MOTD if true and cache exists (default: false)
 * - use_backend_motd: Use live backend server MOTD if true (used when cached MOTD not used or not found)
 * - motd: Custom MOTD in MiniMessage format (optional, used when cached/backend MOTD not used)
 * - version_name: Version name to set (optional)
 * - protocol_version: Protocol version to set (optional, as string like "-1")
 * - icon: Server icon file path or base64 string (optional, must be 64x64 PNG)
 */
public class RespondPingAction implements Action {
    private static final Logger logger = Log.get(RespondPingAction.class);
    private static final String ACTION_NAME = "respond_ping";
    
    private final String pingParam;
    private final Boolean useCachedMotd;
    private final Boolean useBackendMotd;
    private final String motdParam;
    private final String versionName;
    private final String protocolVersion;
    private final String iconParam;
    private final VariableResolver variableResolver;
    private final MotdCacheManager motdCacheManager;
    private final ServerManager serverManager;

    public RespondPingAction(String pingParam, Boolean useCachedMotd, Boolean useBackendMotd,
                            String motdParam, String versionName, String protocolVersion,
                            String iconParam,
                            VariableResolver variableResolver, MotdCacheManager motdCacheManager,
                            ServerManager serverManager) {
        this.pingParam = pingParam;
        this.useCachedMotd = useCachedMotd;
        this.useBackendMotd = useBackendMotd;
        this.motdParam = motdParam;
        this.versionName = versionName;
        this.protocolVersion = protocolVersion;
        this.iconParam = iconParam;
        this.variableResolver = variableResolver;
        this.motdCacheManager = motdCacheManager;
        this.serverManager = serverManager;
    }

    /**
     * Creates a RespondPingAction from configuration.
     */
    public static RespondPingAction create(ActionConfig config, ActionContext ctx) {
        // ping parameter (ServerPing object to use, defaults to ${ping} which means use event.getPing())
        String ping = config.getString("ping", "${ping}");
        
        // Parse top-level use_cached_motd
        Boolean useCachedMotd = null;
        if (config.hasKey("use_cached_motd")) {
            useCachedMotd = config.getBoolean("use_cached_motd", false);
        }

        // Parse top-level use_backend_motd
        Boolean useBackendMotd = null;
        if (config.hasKey("use_backend_motd")) {
            useBackendMotd = config.getBoolean("use_backend_motd", false);
        }
        
        // Parse top-level motd
        String motd = config.getString("motd", null);
        
        // Parse top-level version_name
        String versionName = config.getString("version_name", null);
        
        // Parse top-level protocol_version
        String protocolVersion = config.getString("protocol_version", null);
        
        // Parse top-level icon
        String icon = config.getString("icon", null);
        
        return new RespondPingAction(ping, useCachedMotd, useBackendMotd, motd, versionName, protocolVersion, icon,
                ctx.variableResolver(), ctx.motdCacheManager(), ctx.serverManager());
    }

    @Override
    public ActionType getType() {
        return ActionType.RESPOND_PING;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        
        // Get the ping event object from context - must be wrapped in ${}
        Object pingEventObj = null;
        String pingEventVar = pingParam;
        if (pingEventVar.startsWith("${") && pingEventVar.endsWith("}")) {
            String variableName = VariableResolver.extractVariableName(pingEventVar);
            pingEventObj = variableResolver.resolveVariable(variableName, context);
        }
        
        if (pingEventObj == null) {
            logger.error("({}) {}: No ping event found for '{}'", ruleName, ACTION_NAME, pingEventVar);
            return CompletableFuture.completedFuture(null);
        }
        
        if (!(pingEventObj instanceof ProxyPingEvent)) {
            logger.error("({}) {}: '{}' is not a ProxyPingEvent: {}", 
                    ruleName, ACTION_NAME, pingEventVar, pingEventObj.getClass().getName());
            return CompletableFuture.completedFuture(null);
        }
        
        ProxyPingEvent event = (ProxyPingEvent) pingEventObj;
        
        // Get the ping to use
        ServerPing pingToUse = event.getPing();
        
        // Build new ServerPing with modifications
        ServerPing.Builder pingBuilder = pingToUse.asBuilder();
        
        // Handle MOTD (priority: use_cached_motd > use_backend_motd > motd)
        Component motdComponent = null;

        // Cached MOTD has highest priority
        if (useCachedMotd != null && useCachedMotd && motdCacheManager != null) {
            String virtualHost = (String) context.getVariable("ping.player.virtual_host", null);
            logger.debug("({}) {}: Looking for cached MOTD, useCachedMotd={}, virtualHost='{}', motdCacheManager={}", 
                    ruleName, ACTION_NAME, useCachedMotd, virtualHost, motdCacheManager != null);
            String cachedMotd = motdCacheManager.getCachedMotd(virtualHost);
            if (cachedMotd != null && !cachedMotd.isEmpty()) {
                motdComponent = MiniMessageUtil.parse(cachedMotd);
                logger.debug("({}) {}: Using cached MOTD for virtual host '{}'", 
                        ruleName, ACTION_NAME, virtualHost);
            } else {
                logger.debug("({}) {}: Cached MOTD requested but not found for virtual host '{}'", 
                        ruleName, ACTION_NAME, virtualHost);
            }
        }

        // Backend MOTD (live ping) if cached not used or not found
        if (motdComponent == null && useBackendMotd != null && useBackendMotd && serverManager != null) {
            String serverName = (String) context.getVariable("ping.server", null);
            if (serverName != null) {
                RegisteredServer registeredServer = serverManager.getRegisteredServer(serverName);
                if (registeredServer != null) {
                    try {
                        ServerPing ping = registeredServer.ping().orTimeout(5, TimeUnit.SECONDS).join();
                        Component description = ping.getDescriptionComponent();
                        if (description != null) {
                            motdComponent = description;
                            logger.debug("({}) {}: Using backend MOTD from server '{}'", 
                                    ruleName, ACTION_NAME, serverName);
                        }
                    } catch (Exception e) {
                        logger.warn("({}) {}: Failed to get backend MOTD from server '{}': {}", 
                                ruleName, ACTION_NAME, serverName, e.getMessage());
                        logger.debug("Backend MOTD error details:", e);
                    }
                } else {
                    logger.debug("({}) {}: No RegisteredServer for '{}', skipping backend MOTD", 
                            ruleName, ACTION_NAME, serverName);
                }
            } else {
                logger.debug("({}) {}: use_backend_motd true but ping.server not in context", 
                        ruleName, ACTION_NAME);
            }
        }

        // Custom MOTD only if cached and backend MOTD are not available
        if (motdComponent == null && motdParam != null && !motdParam.isEmpty()) {
            String resolvedMotd = variableResolver.resolve(motdParam, context);
            motdComponent = MiniMessageUtil.parse(resolvedMotd);
            logger.debug("({}) {}: Using custom MOTD", ruleName, ACTION_NAME);
        }
        
        if (motdComponent != null) {
            pingBuilder.description(motdComponent);
        }
        
        // Handle version
        ServerPing.Version originalVersion = pingToUse.getVersion();
        if (originalVersion != null) {
            String versionNameToUse;
            if (versionName != null && !versionName.isEmpty()) {
                // Resolve version name and convert MiniMessage format to legacy format
                String resolvedVersionName = variableResolver.resolve(versionName, context);
                versionNameToUse = MiniMessageUtil.toLegacy(resolvedVersionName);
            } else {
                versionNameToUse = originalVersion.getName();
            }
            int protocolToUse = originalVersion.getProtocol();
            
            if (protocolVersion != null && !protocolVersion.isEmpty()) {
                try {
                    String resolvedProtocolVersion = variableResolver.resolve(protocolVersion, context);
                    protocolToUse = Integer.parseInt(resolvedProtocolVersion);
                } catch (NumberFormatException e) {
                    logger.warn("({}) {}: Invalid protocol_version '{}', using original protocol {}", 
                            ruleName, ACTION_NAME, protocolVersion, originalVersion.getProtocol());
                }
            }
            
            ServerPing.Version newVersion = new ServerPing.Version(protocolToUse, versionNameToUse);
            pingBuilder.version(newVersion);
            logger.debug("({}) {}: Set version name='{}', protocol={}", 
                    ruleName, ACTION_NAME, versionNameToUse, protocolToUse);
        } else if (versionName != null || protocolVersion != null) {
            // Create version if it doesn't exist
            // Use player protocol version as default
            int protocolToUse = -1;
            Object playerProtocolObj = context.getVariable("ping.player.protocol_version");
            if (playerProtocolObj instanceof Integer) {
                protocolToUse = (Integer) playerProtocolObj;
            }
            
            if (protocolVersion != null && !protocolVersion.isEmpty()) {
                try {
                    String resolvedProtocolVersion = variableResolver.resolve(protocolVersion, context);
                    protocolToUse = Integer.parseInt(resolvedProtocolVersion);
                } catch (NumberFormatException e) {
                    logger.warn("({}) {}: Invalid protocol_version '{}', using player protocol {}", 
                            ruleName, ACTION_NAME, protocolVersion, protocolToUse);
                }
            }
            
            String versionNameToUse = versionName != null ? 
                    MiniMessageUtil.toLegacy(variableResolver.resolve(versionName, context)) : "Unknown";
            ServerPing.Version newVersion = new ServerPing.Version(protocolToUse, versionNameToUse);
            pingBuilder.version(newVersion);
            logger.debug("({}) {}: Created version name='{}', protocol={}", 
                    ruleName, ACTION_NAME, versionNameToUse, protocolToUse);
        }
        
        // Handle icon
        if (iconParam != null && !iconParam.isEmpty()) {
            String resolvedIcon = variableResolver.resolve(iconParam, context);
            java.awt.image.BufferedImage iconImage = IconUtil.loadIconAsImage(resolvedIcon);
            if (iconImage != null) {
                try {
                    Favicon favicon = Favicon.create(iconImage);
                    pingBuilder.favicon(favicon);
                    logger.debug("({}) {}: Set server icon", ruleName, ACTION_NAME);
                } catch (Exception e) {
                    logger.warn("({}) {}: Failed to create favicon from '{}': {}", 
                            ruleName, ACTION_NAME, resolvedIcon, e.getMessage());
                    logger.debug("Favicon creation error details:", e);
                }
            } else {
                logger.warn("({}) {}: Failed to load icon from '{}'", 
                        ruleName, ACTION_NAME, resolvedIcon);
            }
        }
        
        // Build the new ping and set it on the event
        ServerPing newPing = pingBuilder.build();
        event.setPing(newPing);
        
        // Allow the ping by setting the result to allowed
        event.setResult(ResultedEvent.GenericResult.allowed());
        
        logger.debug("({}) {}: Responded to ping request with custom data", ruleName, ACTION_NAME);
        
        // Signal the ping trigger to release the event immediately
        context.releaseEvent();
        
        return CompletableFuture.completedFuture(null);
    }
}
