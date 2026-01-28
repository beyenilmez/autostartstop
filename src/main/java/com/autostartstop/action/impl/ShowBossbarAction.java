package com.autostartstop.action.impl;

import com.autostartstop.action.AbstractPlayerTargetingAction;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import com.autostartstop.util.MiniMessageUtil;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a bossbar to targeted players.
 * The bossbar is tracked by ID and can be hidden later with HideBossbarAction.
 */
public class ShowBossbarAction extends AbstractPlayerTargetingAction {
    private static final Logger logger = Log.get(ShowBossbarAction.class);
    
    /** Active bossbars keyed by ID, then by player UUID. */
    private static final Map<String, Map<UUID, BossBar>> activeBossbars = new ConcurrentHashMap<>();
    
    private final String id;
    private final String message;
    private final String colorRaw;
    private final String overlayRaw;
    private final String progressRaw;

    public ShowBossbarAction(String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            String id, String message, String color, String overlay, String progress,
            TargetResolver targetResolver) {
        super(playerParam, playersParam, serverParam, serversParam, targetResolver);
        this.id = id;
        this.message = message;
        this.colorRaw = color;
        this.overlayRaw = overlay;
        this.progressRaw = progress;
    }

    public static ShowBossbarAction create(ActionConfig config, ActionContext ctx) {
        return new ShowBossbarAction(
                config.getString("player"),
                config.getStringList("players"),
                config.getString("server"),
                config.getStringList("servers"),
                config.requireString("id"),
                config.requireString("message"),
                config.getString("color", "WHITE"),
                config.getString("overlay", "PROGRESS"),
                config.getString("progress", "1.0"),
                ctx.targetResolver()
        );
    }

    @Override
    public ActionType getType() {
        return ActionType.SHOW_BOSSBAR;
    }

    @Override
    protected void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName) {
        var resolver = getTargetResolver().getVariableResolver();
        
        String resolvedId = resolver.resolve(id, context);
        if (resolvedId == null || resolvedId.isBlank()) {
            logger.error("({}) {}: No bossbar id provided", ruleName, getActionName());
            return;
        }
        
        if (message == null || message.isBlank()) {
            logger.error("({}) {}: No message provided", ruleName, getActionName());
            return;
        }
        
        Component messageComponent = MiniMessageUtil.parse(resolver.resolve(message, context));
        BossBar.Color color = resolver.resolveEnum(colorRaw, context, BossBar.Color.class, BossBar.Color.WHITE);
        BossBar.Overlay overlay = resolver.resolveEnum(overlayRaw, context, BossBar.Overlay.class, BossBar.Overlay.PROGRESS);
        float progress = resolver.resolveFloatClamped(progressRaw, context, 1.0f, 0.0f, 1.0f);
        
        Map<UUID, BossBar> playerBossbars = activeBossbars.computeIfAbsent(resolvedId, k -> new ConcurrentHashMap<>());
        
        int shownCount = 0;
        for (Player player : players) {
            try {
                UUID playerUuid = player.getUniqueId();
                
                BossBar existingBar = playerBossbars.remove(playerUuid);
                if (existingBar != null) {
                    player.hideBossBar(existingBar);
                }
                
                BossBar bossBar = BossBar.bossBar(messageComponent, progress, color, overlay);
                player.showBossBar(bossBar);
                playerBossbars.put(playerUuid, bossBar);
                shownCount++;
            } catch (Exception e) {
                logger.error("({}) {}: Failed for player '{}': {}", 
                        ruleName, getActionName(), player.getUsername(), e.getMessage());
            }
        }
        
        logger.debug("({}) {}: Showed '{}' to {} player(s)", ruleName, getActionName(), resolvedId, shownCount);
    }

    /** Returns the active bossbars map for use by HideBossbarAction. */
    public static Map<String, Map<UUID, BossBar>> getActiveBossbars() {
        return activeBossbars;
    }

    public String getId() { return id; }
    public String getMessage() { return message; }
    public String getColorRaw() { return colorRaw; }
    public String getOverlayRaw() { return overlayRaw; }
    public String getProgressRaw() { return progressRaw; }
}
