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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Sends a title and optional subtitle to targeted players.
 */
public class SendTitleAction extends AbstractPlayerTargetingAction {
    private static final Logger logger = Log.get(SendTitleAction.class);
    
    private static final Duration DEFAULT_FADE_IN = Duration.ofMillis(500);
    private static final Duration DEFAULT_STAY = Duration.ofSeconds(3);
    private static final Duration DEFAULT_FADE_OUT = Duration.ofMillis(500);
    
    private final String title;
    private final String subtitle;
    private final String fadeInRaw;
    private final String stayRaw;
    private final String fadeOutRaw;

    public SendTitleAction(String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            String title, String subtitle,
            String fadeIn, String stay, String fadeOut,
            TargetResolver targetResolver) {
        super(playerParam, playersParam, serverParam, serversParam, targetResolver);
        this.title = title;
        this.subtitle = subtitle;
        this.fadeInRaw = fadeIn;
        this.stayRaw = stay;
        this.fadeOutRaw = fadeOut;
    }

    public static SendTitleAction create(ActionConfig config, ActionContext ctx) {
        return new SendTitleAction(
                config.getString("player"),
                config.getStringList("players"),
                config.getString("server"),
                config.getStringList("servers"),
                config.getString("title"),
                config.getString("subtitle"),
                config.getString("fade_in", "500ms"),
                config.getString("stay", "3s"),
                config.getString("fade_out", "500ms"),
                ctx.targetResolver()
        );
    }

    @Override
    public ActionType getType() {
        return ActionType.SEND_TITLE;
    }

    @Override
    protected void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName) {
        var resolver = getTargetResolver().getVariableResolver();
        
        Component titleComponent = Component.empty();
        if (title != null && !title.isBlank()) {
            titleComponent = MiniMessageUtil.parse(resolver.resolve(title, context));
        }
        
        Component subtitleComponent = Component.empty();
        if (subtitle != null && !subtitle.isBlank()) {
            subtitleComponent = MiniMessageUtil.parse(resolver.resolve(subtitle, context));
        }
        
        Duration fadeIn = resolver.resolveDuration(fadeInRaw, context, DEFAULT_FADE_IN);
        Duration stay = resolver.resolveDuration(stayRaw, context, DEFAULT_STAY);
        Duration fadeOut = resolver.resolveDuration(fadeOutRaw, context, DEFAULT_FADE_OUT);
        
        Title.Times times = Title.Times.times(fadeIn, stay, fadeOut);
        Title titleObj = Title.title(titleComponent, subtitleComponent, times);
        
        int sentCount = 0;
        for (Player player : players) {
            try {
                player.showTitle(titleObj);
                sentCount++;
            } catch (Exception e) {
                logger.error("({}) {}: Failed for player '{}': {}", 
                        ruleName, getActionName(), player.getUsername(), e.getMessage());
            }
        }
        
        logger.debug("({}) {}: Sent to {} player(s)", ruleName, getActionName(), sentCount);
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getFadeInRaw() { return fadeInRaw; }
    public String getStayRaw() { return stayRaw; }
    public String getFadeOutRaw() { return fadeOutRaw; }
}
