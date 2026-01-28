package com.autostartstop.template.impl;

import com.autostartstop.Log;
import com.autostartstop.action.impl.StopAction;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.template.Template;
import com.autostartstop.template.TemplateContext;
import com.autostartstop.template.TemplateType;
import com.autostartstop.trigger.impl.EmptyServerTrigger;
import com.autostartstop.util.DurationUtil;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Template that stops servers when they become empty for a specified duration.
 * 
 * Configuration:
 * - empty_time: Duration the server must be empty before stopping (default: 15m)
 * - servers: List of server names to monitor
 * 
 * Uses EmptyServerTrigger internally to monitor servers and StopAction to stop them.
 */
public class StopOnEmptyTemplate implements Template {
    private static final Logger logger = Log.get(StopOnEmptyTemplate.class);

    private final TemplateContext context;
    private final Duration emptyTime;
    private final List<String> servers;
    
    private String ruleName;
    private EmptyServerTrigger trigger;
    private boolean activated = false;

    /**
     * Creates a StopOnEmptyTemplate from the given configuration.
     */
    public static StopOnEmptyTemplate create(TemplateConfig config, TemplateContext context) {
        Duration emptyTime = config.getEmptyTime();
        List<String> servers = config.getServers();
        
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("stop_on_empty template requires at least one server");
        }

        return new StopOnEmptyTemplate(context, emptyTime, servers);
    }

    private StopOnEmptyTemplate(TemplateContext context, Duration emptyTime, List<String> servers) {
        this.context = context;
        this.emptyTime = emptyTime;
        this.servers = servers;
    }

    @Override
    public TemplateType getType() {
        return TemplateType.STOP_ON_EMPTY;
    }

    @Override
    public void activate(String ruleName) {
        if (activated) {
            return;
        }
        
        this.ruleName = ruleName;
        logger.debug("StopOnEmptyTemplate: activating for rule '{}' (empty_time: {}, servers: {})",
                ruleName, DurationUtil.format(emptyTime), servers);

        // Get check interval from settings
        Duration checkInterval = Duration.ofMinutes(5); // Default
        if (context.settings() != null && context.settings().getEmptyServerCheckInterval() != null) {
            try {
                checkInterval = DurationUtil.parse(context.settings().getEmptyServerCheckInterval());
            } catch (Exception e) {
                logger.warn("StopOnEmptyTemplate: invalid empty_server_check_interval '{}', using default {}",
                        context.settings().getEmptyServerCheckInterval(), DurationUtil.format(checkInterval));
            }
        }

        // Create server list config
        Map<String, Object> serverListMap = new HashMap<>();
        serverListMap.put("mode", "whitelist");
        serverListMap.put("servers", servers);
        TriggerConfig.ServerListConfig serverList = new TriggerConfig.ServerListConfig(serverListMap);

        // Create the trigger directly
        trigger = new EmptyServerTrigger(
                context.proxy(),
                context.plugin(),
                context.serverManager(),
                emptyTime,
                checkInterval,
                serverList);

        // Create execution callback that stops the server
        Function<ExecutionContext, CompletableFuture<Void>> executionCallback = context -> {
            // Set rule name in context for actions
            context.setVariable("_rule_name", ruleName);
            
            Object serverNameObj = context.getVariable("empty_server.server.name");
            if (serverNameObj == null) {
                logger.warn("StopOnEmptyTemplate: empty_server.server.name not found in context, cannot stop server");
                return CompletableFuture.completedFuture(null);
            }
            String serverName = serverNameObj.toString();

            logger.debug("StopOnEmptyTemplate: server '{}' has been empty for {}, stopping...", 
                    serverName, DurationUtil.format(emptyTime));

            // Create and execute the action directly
            StopAction stopAction = new StopAction(
                    serverName,
                    this.context.serverManager(),
                    this.context.variableResolver(),
                    this.context.motdCacheManager());

            return stopAction.execute(context);
        };

        // Activate the trigger
        trigger.activate(ruleName, executionCallback);
        activated = true;
        
        logger.debug("StopOnEmptyTemplate: activated for rule '{}'", ruleName);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("StopOnEmptyTemplate: deactivating for rule '{}'", ruleName);

        if (trigger != null) {
            trigger.deactivate();
            trigger = null;
        }

        this.ruleName = null;
        activated = false;

        logger.debug("StopOnEmptyTemplate: deactivated for rule '{}'", ruleName);
    }
}
