package com.autostartstop;

import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionRegistry;
import com.autostartstop.api.ServerControlApiRegistry;
import com.autostartstop.api.impl.AmpServerControlApi;
import com.autostartstop.command.CommandManager;
import com.autostartstop.command.impl.ReloadCommand;
import com.autostartstop.command.impl.TriggerCommand;
import com.autostartstop.condition.ConditionContext;
import com.autostartstop.condition.ConditionEvaluator;
import com.autostartstop.condition.ConditionRegistry;
import com.autostartstop.config.ConfigLoader;
import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.SettingsConfig;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.rule.RuleExecutor;
import com.autostartstop.rule.RuleManager;
import com.autostartstop.server.MotdCacheManager;
import com.autostartstop.server.ServerManager;
import com.autostartstop.server.ServerStartupTracker;
import com.autostartstop.server.StartupTimeTracker;
import com.autostartstop.template.TemplateContext;
import com.autostartstop.template.TemplateRegistry;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerRegistry;
import com.autostartstop.metrics.MetricsManager;
import com.autostartstop.util.CommandExecutor;
import com.autostartstop.util.DurationUtil;
import com.autostartstop.util.TargetResolver;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

@Plugin(id = "autostartstop", name = "AutoStartStop", version = "1.0.1-beta", authors = {
        "beyenilmez" }, description = "Automated server management with rule-based triggers and actions")
public class AutoStartStop {
    private static final Logger logger = Log.get(AutoStartStop.class);

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final PluginContainer pluginContainer;
    private final Metrics.Factory metricsFactory;

    // Plugin components
    private volatile ConfigLoader configLoader;
    private volatile PluginConfig pluginConfig;
    private volatile VariableResolver variableResolver;
    private volatile TriggerRegistry triggerRegistry;
    private volatile ActionRegistry actionRegistry;
    private volatile ConditionRegistry conditionRegistry;
    private volatile ConditionEvaluator conditionEvaluator;
    private volatile TemplateRegistry templateRegistry;
    private volatile ServerControlApiRegistry apiRegistry;
    private volatile ServerManager serverManager;
    private volatile StartupTimeTracker startupTimeTracker;
    private volatile ServerStartupTracker serverStartupTracker;
    private volatile MotdCacheManager motdCacheManager;
    private volatile RuleManager ruleManager;
    private volatile RuleExecutor ruleExecutor;
    private volatile CommandManager commandManager;
    @SuppressWarnings("unused")
    private volatile MetricsManager metricsManager;

    /**
     * Lock for configuration reload operations.
     */
    private final Object reloadLock = new Object();

    @Inject
    public AutoStartStop(ProxyServer proxy, Logger velocityLogger,
            @DataDirectory Path dataDirectory,
            PluginContainer pluginContainer,
            Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.pluginContainer = pluginContainer;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Enabling AutoStartStop...");
        logger.debug("Data directory: {}", dataDirectory);
        logger.debug("Plugin version: {}", pluginContainer.getDescription().getVersion().orElse("unknown"));

        try {
            // Initialize components
            logger.debug("Phase 1/4: Initializing core components...");
            initializeComponents();
            logger.debug("Core components initialized");

            // Load configuration (this activates triggers)
            logger.debug("Phase 2/4: Loading configuration and activating triggers...");
            loadConfiguration();

            // Initialize metrics (after config is loaded to check metrics setting)
            logger.debug("Phase 3/5: Initializing metrics...");
            initializeMetrics();

            // Register commands
            logger.debug("Phase 4/5: Registering commands...");
            registerCommands();

            logger.debug("Phase 5/5: Initialization complete");
            logger.info("AutoStartStop enabled successfully");

        } catch (Exception e) {
            logger.error("Failed to enable AutoStartStop: {}", e.getMessage(), e);
        }
    }

    // Use a lower priority than 100 so shutdown triggers complete before cleanup
    @Subscribe(priority = 50)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Disabling AutoStartStop...");

        // Stop MOTD cache manager
        if (motdCacheManager != null) {
            motdCacheManager.stop();
        }

        // Get shutdown timeout from config
        Duration shutdownTimeout = Duration.ofSeconds(30);
        if (pluginConfig != null && pluginConfig.getSettings() != null) {
            try {
                shutdownTimeout = DurationUtil.parse(pluginConfig.getSettings().getShutdownTimeout());
                logger.debug("Using configured shutdown timeout: {}", pluginConfig.getSettings().getShutdownTimeout());
            } catch (Exception e) {
                logger.warn("Invalid shutdown_timeout in config, using default 30s");
            }
        } else {
            logger.debug("No shutdown timeout configured, using default 30s");
        }

        // Note: proxy_shutdown triggers will fire via their event subscriptions
        // They are self-contained and will receive the ProxyShutdownEvent

        // Signal executor to stop accepting new tasks
        if (ruleExecutor != null) {
            logger.debug("Initiating rule executor shutdown...");
            ruleExecutor.shutdown();

            // Wait for pending actions with timeout
            try {
                logger.debug("Waiting up to {} for pending actions to complete...", shutdownTimeout);
                boolean completed = ruleExecutor.awaitTermination(shutdownTimeout);
                if (completed) {
                    logger.debug("All pending actions completed");
                } else {
                    logger.warn("Shutdown timeout reached, some actions may have been interrupted");
                    ruleExecutor.shutdownNow();
                }
            } catch (Exception e) {
                logger.error("Error during shutdown: {}", e.getMessage(), e);
                ruleExecutor.shutdownNow();
            }
        } else {
            logger.debug("Rule executor was null, skipping executor shutdown");
        }

        // Deactivate all rules (unregisters triggers from event manager)
        if (ruleManager != null) {
            logger.debug("Deactivating all rules...");
            ruleManager.clear();
            logger.debug("All rules deactivated");
        }

        // Unregister commands
        if (commandManager != null) {
            logger.debug("Unregistering commands...");
            commandManager.unregister();
            logger.debug("Commands unregistered");
        }

        // Shutdown command executor
        logger.debug("Shutting down command executor...");
        CommandExecutor.shutdown();

        // Shutdown AMP API executor
        logger.debug("Shutting down AMP API executor...");
        AmpServerControlApi.shutdown();

        logger.info("AutoStartStop disabled");
    }

    /**
     * Initializes all plugin components.
     */
    private void initializeComponents() {
        logger.debug("Creating variable resolver...");
        variableResolver = new VariableResolver();

        logger.debug("Creating registries...");
        triggerRegistry = new TriggerRegistry();
        actionRegistry = new ActionRegistry();
        conditionRegistry = new ConditionRegistry();
        templateRegistry = new TemplateRegistry();
        apiRegistry = new ServerControlApiRegistry();
        logger.debug("All registries created: trigger, action, condition, template, api");

        logger.debug("Registering control API factories...");
        registerApiFactories();

        logger.debug("Creating server manager...");
        serverManager = new ServerManager(proxy, apiRegistry);

        logger.debug("Creating startup time tracker...");
        startupTimeTracker = new StartupTimeTracker(dataDirectory);

        logger.debug("Creating server startup tracker...");
        serverStartupTracker = new ServerStartupTracker(serverManager, startupTimeTracker);

        logger.debug("Injecting dependencies into VariableResolver for global server variables...");
        variableResolver.setServerManager(serverManager);
        variableResolver.setServerStartupTracker(serverStartupTracker);

        // Register trigger factories AFTER serverManager is created (they need
        // dependencies)
        logger.debug("Registering trigger factories...");
        registerTriggerFactories();

        logger.debug("Registering condition factories...");
        registerConditionFactories();

        logger.debug("Creating condition evaluator...");
        conditionEvaluator = new ConditionEvaluator(conditionRegistry);

        logger.debug("Registering action factories...");
        registerActionFactories();

        logger.debug("Creating rule executor...");
        ruleExecutor = new RuleExecutor(actionRegistry, conditionEvaluator);

        logger.debug("Registering template factories...");
        registerTemplateFactories();

        logger.debug("Creating rule manager...");
        ruleManager = new RuleManager(triggerRegistry, templateRegistry);

        logger.debug("Creating config loader for directory: {}", dataDirectory);
        configLoader = new ConfigLoader(dataDirectory);
        
        // Will be initialized after config is loaded (needs settings)
        motdCacheManager = null;
    }

    /**
     * Registers trigger creators via TriggerContext.
     * All triggers now use static create methods registered in TriggerType.
     * Note: TriggerContext will be updated with actual settings after config is loaded.
     */
    private void registerTriggerFactories() {
        // Set up initial TriggerContext with default settings
        // This will be updated with actual config settings in updateTriggerContext()
        TriggerContext triggerContext = TriggerContext.builder()
                .proxy(proxy)
                .plugin(this)
                .serverManager(serverManager)
                .motdCacheManager(null) // Will be set after config is loaded
                .settings(new SettingsConfig())
                .build();
        triggerRegistry.setTriggerContext(triggerContext);
        
        int creatorCount = triggerRegistry.autoRegisterCreators();
        logger.debug("All trigger creators registered ({} total)", creatorCount);
    }

    /**
     * Registers action creators via ActionContext.
     * All actions now use static create methods registered in ActionType.
     * Note: ActionContext will be updated with actual settings after config is loaded.
     */
    private void registerActionFactories() {
        // Create shared TargetResolver for player/server targeting actions
        TargetResolver targetResolver = new TargetResolver(variableResolver, proxy);
        
        // Set up initial ActionContext with default settings
        // This will be updated with actual config settings in updateAllContexts()
        ActionContext actionContext = ActionContext.builder()
                .serverManager(serverManager)
                .variableResolver(variableResolver)
                .startupTracker(serverStartupTracker)
                .conditionEvaluator(conditionEvaluator)
                .targetResolver(targetResolver)
                .actionRegistry(actionRegistry)
                .settings(new SettingsConfig())
                .motdCacheManager(null) // Will be set after config is loaded
                .build();
        actionRegistry.setActionContext(actionContext);
        
        int creatorCount = actionRegistry.autoRegisterCreators();
        logger.debug("All action creators registered ({} total)", creatorCount);
    }

    /**
     * Registers condition creators via ConditionContext.
     * All conditions now use static create methods registered in ConditionType.
     * Note: ConditionContext will be updated with actual settings after config is loaded.
     */
    private void registerConditionFactories() {
        // Set up initial ConditionContext with default settings
        // This will be updated with actual config settings in updateAllContexts()
        ConditionContext conditionContext = ConditionContext.builder()
                .variableResolver(variableResolver)
                .serverManager(serverManager)
                .settings(new SettingsConfig())
                .build();
        conditionRegistry.setConditionContext(conditionContext);
        
        int creatorCount = conditionRegistry.autoRegisterCreators();
        logger.debug("All condition creators registered ({} total)", creatorCount);
    }

    /**
     * Registers template creators via TemplateContext.
     * All templates use static create methods registered in TemplateType.
     * Note: TemplateContext will be updated with actual settings after config is loaded.
     */
    private void registerTemplateFactories() {
        // Set up initial TemplateContext with default settings
        // This will be updated with actual config settings in updateAllContexts()
        TemplateContext templateContext = TemplateContext.builder()
                .proxy(proxy)
                .plugin(this)
                .serverManager(serverManager)
                .startupTracker(serverStartupTracker)
                .triggerRegistry(triggerRegistry)
                .actionRegistry(actionRegistry)
                .ruleExecutor(ruleExecutor)
                .variableResolver(variableResolver)
                .settings(new SettingsConfig())
                .motdCacheManager(null) // Will be set after config is loaded
                .build();
        templateRegistry.setTemplateContext(templateContext);
        
        int creatorCount = templateRegistry.autoRegisterCreators();
        logger.debug("All template creators registered ({} total)", creatorCount);
    }

    /**
     * Registers control API creators.
     * All control APIs now use static create methods registered in ServerControlApiType.
     */
    private void registerApiFactories() {
        int creatorCount = apiRegistry.autoRegisterCreators();
        logger.debug("All control API creators registered ({} total)", creatorCount);
    }

    /**
     * Loads the plugin configuration and activates triggers.
     */
    private void loadConfiguration() throws IOException {
        logger.debug("Loading plugin configuration from disk...");
        pluginConfig = configLoader.load();
        logger.debug("Configuration version: {}", pluginConfig.getVersion());

        // Initialize MOTD cache manager
        logger.debug("Initializing MOTD cache manager...");
        initializeMotdCacheManager();

        // Update all contexts with loaded settings (now includes motdCacheManager)
        updateAllContexts(false);

        logger.debug("Loading servers from configuration...");
        serverManager.loadServers(pluginConfig);

        logger.debug("Loading rules from configuration and activating triggers...");
        ruleManager.loadRules(pluginConfig, ruleExecutor);
    }

    /**
     * Initializes bStats metrics.
     * bStats respects its own global configuration in plugins/bStats/config.txt
     */
    private void initializeMetrics() {
        try {
            metricsManager = new MetricsManager(this, metricsFactory, () -> pluginConfig);
            logger.debug("Metrics initialized successfully");
        } catch (Exception e) {
            logger.warn("Failed to initialize metrics: {}", e.getMessage());
            logger.debug("Metrics initialization error details:", e);
        }
    }
    
    /**
     * Initializes and starts the MOTD cache manager.
     */
    private void initializeMotdCacheManager() {
        SettingsConfig settings = pluginConfig.getSettings();
        if (settings == null) {
            logger.debug("No settings available, skipping MOTD cache manager initialization");
            return;
        }
        
        try {
            Duration cacheInterval = DurationUtil.parse(settings.getMotdCacheInterval());
            
            // Stop existing manager if it exists
            if (motdCacheManager != null) {
                motdCacheManager.stop();
            }
            
            // Create and start new manager
            motdCacheManager = new MotdCacheManager(proxy, serverManager, dataDirectory, cacheInterval);
            motdCacheManager.start(this);
            
            logger.debug("MOTD cache manager initialized with interval: {}", DurationUtil.format(cacheInterval));
        } catch (Exception e) {
            logger.error("Failed to initialize MOTD cache manager: {}", e.getMessage());
            logger.debug("MOTD cache manager initialization error details:", e);
            motdCacheManager = null;
        }
    }

    /**
     * Updates all contexts (Trigger, Action, Condition, Template) with the current plugin settings.
     * Called after config is loaded/reloaded to ensure all contexts have access to the actual settings.
     * 
     * @param isReload true if this is a configuration reload, false for initial load
     */
    private void updateAllContexts(boolean isReload) {
        SettingsConfig settings = pluginConfig.getSettings();
        
        // Update TriggerContext
        TriggerContext triggerContext = TriggerContext.builder()
                .proxy(proxy)
                .plugin(this)
                .serverManager(serverManager)
                .motdCacheManager(motdCacheManager)
                .settings(settings)
                .isReload(isReload)
                .build();
        triggerRegistry.setTriggerContext(triggerContext);
        
        // Update ActionContext
        TargetResolver targetResolver = new TargetResolver(variableResolver, proxy);
        ActionContext actionContext = ActionContext.builder()
                .serverManager(serverManager)
                .variableResolver(variableResolver)
                .startupTracker(serverStartupTracker)
                .conditionEvaluator(conditionEvaluator)
                .targetResolver(targetResolver)
                .actionRegistry(actionRegistry)
                .settings(settings)
                .motdCacheManager(motdCacheManager)
                .build();
        actionRegistry.setActionContext(actionContext);
        
        // Update ConditionContext
        ConditionContext conditionContext = ConditionContext.builder()
                .variableResolver(variableResolver)
                .serverManager(serverManager)
                .settings(settings)
                .build();
        conditionRegistry.setConditionContext(conditionContext);
        
        // Update TemplateContext
        TemplateContext templateContext = TemplateContext.builder()
                .proxy(proxy)
                .plugin(this)
                .serverManager(serverManager)
                .startupTracker(serverStartupTracker)
                .triggerRegistry(triggerRegistry)
                .actionRegistry(actionRegistry)
                .ruleExecutor(ruleExecutor)
                .variableResolver(variableResolver)
                .settings(settings)
                .motdCacheManager(motdCacheManager)
                .build();
        templateRegistry.setTemplateContext(templateContext);
        
        logger.debug("All contexts updated with loaded settings");
    }

    /**
     * Reloads the plugin configuration.
     * Thread-safe: synchronized to prevent concurrent reloads.
     */
    public void reloadConfiguration() {
        synchronized (reloadLock) {
            logger.debug("Reloading configuration...");

            try {
                pluginConfig = configLoader.reload();
                logger.debug("Configuration reloaded, version: {}", pluginConfig.getVersion());

                // Update all contexts with reloaded settings (isReload=true to prevent proxy_start from firing again)
                updateAllContexts(true);

                logger.debug("Clearing existing server definitions...");
                serverManager.clear();
                serverManager.loadServers(pluginConfig);
                
                // Reinitialize MOTD cache manager with new settings
                initializeMotdCacheManager();

                logger.debug("Clearing existing rule definitions (deactivating triggers)...");
                ruleManager.clear();

                logger.debug("Loading rules and activating triggers...");
                ruleManager.loadRules(pluginConfig, ruleExecutor);

                logger.info("Configuration reloaded (version: {}, servers: {}, rules: {})", 
                        pluginConfig.getVersion(), 
                        pluginConfig.getServers() != null ? pluginConfig.getServers().size() : 0,
                        pluginConfig.getRules() != null ? pluginConfig.getRules().size() : 0);
            } catch (IOException e) {
                logger.error("Failed to reload configuration: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to reload configuration: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Registers commands.
     */
    private void registerCommands() {
        commandManager = new CommandManager(this, proxy);

        // Register subcommands
        commandManager.registerSubCommand(new ReloadCommand(this::reloadConfiguration));
        commandManager.registerSubCommand(new TriggerCommand(ruleManager));

        // Register main command with Velocity
        commandManager.register();
    }
}
