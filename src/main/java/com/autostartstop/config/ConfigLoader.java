package com.autostartstop.config;

import com.autostartstop.Log;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads and parses the plugin configuration using BoostedYAML.
 * Uses ConfigParser for parsing individual configuration sections.
 */
public class ConfigLoader {
    private static final Logger logger = Log.get(ConfigLoader.class);
    
    private final Path dataDirectory;
    private final ConfigMerger configMerger;
    private YamlDocument configDocument;
    private PluginConfig pluginConfig;

    public ConfigLoader(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.configMerger = new ConfigMerger();
    }

    /**
     * Loads the configuration from file.
     *
     * @return The loaded plugin configuration
     * @throws IOException if the configuration cannot be loaded
     */
    public PluginConfig load() throws IOException {
        logger.debug("Starting configuration load process...");
        
        // Ensure data directory exists
        if (!Files.exists(dataDirectory)) {
            logger.debug("Creating data directory: {}", dataDirectory);
            Files.createDirectories(dataDirectory);
        }

        Path configPath = dataDirectory.resolve("config.yml");
        logger.debug("Configuration file path: {}", configPath);

        // Load default config from resources
        InputStream defaultConfig = getClass().getResourceAsStream("/config.yml");
        if (defaultConfig == null) {
            throw new IOException("Default config.yml not found in resources");
        }

        // Load or create config file
        boolean configExists = Files.exists(configPath);
        
        configDocument = YamlDocument.create(
                configPath.toFile(),
                defaultConfig,
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).build()
        );
        
        logger.debug(configExists ? "Loaded existing config" : "Created new config from defaults");

        // Parse the configuration
        pluginConfig = parseConfig();
        logger.info("Configuration loaded (version: {}, servers: {}, rules: {})",
                pluginConfig.getVersion(),
                pluginConfig.getServers() != null ? pluginConfig.getServers().size() : 0,
                pluginConfig.getRules() != null ? pluginConfig.getRules().size() : 0);

        return pluginConfig;
    }

    /**
     * Reloads the configuration from file.
     *
     * @return The reloaded plugin configuration
     * @throws IOException if the configuration cannot be reloaded
     */
    public PluginConfig reload() throws IOException {
        if (configDocument != null) {
            configDocument.reload();
            pluginConfig = parseConfig();
            logger.info("Configuration reloaded (version: {})", pluginConfig.getVersion());
        } else {
            return load();
        }
        return pluginConfig;
    }

    /**
     * Gets the current plugin configuration.
     */
    public PluginConfig getConfig() {
        return pluginConfig;
    }

    /**
     * Parses the YAML document into a PluginConfig object.
     */
    private PluginConfig parseConfig() {
        PluginConfig config = new PluginConfig();

        // Parse version
        config.setVersion(configDocument.getInt("version", 1));

        // Parse settings
        config.setSettings(parseSettings());

        // Parse defaults
        config.setDefaults(parseDefaults());

        // Parse servers (with defaults merge)
        config.setServers(parseServers(config.getDefaults()));

        // Parse rules
        config.setRules(parseRules());

        return config;
    }

    /**
     * Parses the settings section.
     */
    private SettingsConfig parseSettings() {
        Section section = configDocument.getSection("settings");
        return ConfigParser.parseSettings(section);
    }

    /**
     * Parses the defaults section.
     */
    private DefaultsConfig parseDefaults() {
        DefaultsConfig defaults = new DefaultsConfig();

        Section section = configDocument.getSection("defaults");
        if (section != null) {
            Section serverSection = section.getSection("server");
            if (serverSection != null) {
                defaults.setServer(ConfigParser.parseServer(serverSection, null));
            }
        }

        return defaults;
    }

    /**
     * Parses the servers section.
     */
    private Map<String, ServerConfig> parseServers(DefaultsConfig defaults) {
        Map<String, ServerConfig> servers = new HashMap<>();

        Section section = configDocument.getSection("servers");
        if (section == null) {
            logger.warn("No servers section found in configuration");
            return servers;
        }

        Set<?> serverKeys = section.getKeys();
        logger.debug("Found {} server definitions", serverKeys.size());
        
        for (Object key : serverKeys) {
            String serverName = key.toString();
            Section serverSection = section.getSection(serverName);
            
            if (serverSection == null) {
                logger.warn("Server section '{}' is empty or invalid", serverName);
                continue;
            }

            ServerConfig serverConfig = ConfigParser.parseServer(serverSection, serverName);

            // Merge with defaults
            ServerConfig defaultServer = defaults != null ? defaults.getServer() : null;
            serverConfig = configMerger.mergeServerConfig(serverConfig, defaultServer);
            serverConfig.setName(serverName);

            servers.put(serverName, serverConfig);
            
            String apiType = serverConfig.getControlApi() != null 
                    ? serverConfig.getControlApi().getType() 
                    : "none";
            logger.debug("Server '{}' parsed (api_type: {})", serverName, apiType);
        }

        return servers;
    }

    /**
     * Parses the rules section.
     */
    private Map<String, RuleConfig> parseRules() {
        Map<String, RuleConfig> rules = new HashMap<>();

        Section section = configDocument.getSection("rules");
        if (section == null) {
            logger.warn("No rules section found in configuration");
            return rules;
        }

        Set<?> ruleKeys = section.getKeys();
        logger.debug("Found {} rule definitions", ruleKeys.size());
        
        for (Object key : ruleKeys) {
            String ruleName = key.toString();
            Section ruleSection = section.getSection(ruleName);
            
            if (ruleSection == null) {
                logger.warn("Rule section '{}' is empty or invalid", ruleName);
                continue;
            }

            RuleConfig ruleConfig = ConfigParser.parseRule(ruleSection, ruleName);
            rules.put(ruleName, ruleConfig);

            int triggerCount = ruleConfig.getTriggers() != null ? ruleConfig.getTriggers().size() : 0;
            int actionCount = ruleConfig.getActions() != null ? ruleConfig.getActions().size() : 0;
            boolean hasConditions = ruleConfig.getConditions() != null && !ruleConfig.getConditions().isEmpty();
            
            logger.debug("Rule '{}' parsed: {} triggers, {} actions, conditions: {}", 
                    ruleName, triggerCount, actionCount, hasConditions);
        }

        return rules;
    }
}
