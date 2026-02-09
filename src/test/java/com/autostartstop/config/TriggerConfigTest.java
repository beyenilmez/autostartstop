package com.autostartstop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TriggerConfig")
class TriggerConfigTest {

    @Nested
    @DisplayName("Default values for convenience getters")
    class DefaultsTests {

        @Test
        @DisplayName("isDenyConnection should default to false")
        void denyConnectionDefault() {
            TriggerConfig config = withRaw("connection", Map.of());
            assertFalse(config.isDenyConnection());
        }

        @Test
        @DisplayName("isHoldResponse should default to false")
        void holdResponseDefault() {
            TriggerConfig config = withRaw("ping", Map.of());
            assertFalse(config.isHoldResponse());
        }

        @Test
        @DisplayName("getEmptyTime should default to 15 minutes")
        void emptyTimeDefault() {
            TriggerConfig config = withRaw("empty_server", Map.of());
            assertEquals(Duration.ofMinutes(15), config.getEmptyTime());
        }

        @Test
        @DisplayName("getEmptyTime should parse configured duration")
        void emptyTimeConfigured() {
            TriggerConfig config = withRaw("empty_server", Map.of("empty_time", "30m"));
            assertEquals(Duration.ofMinutes(30), config.getEmptyTime());
        }
    }

    @Nested
    @DisplayName("PlayerListConfig")
    class PlayerListConfigTests {

        @Test
        @DisplayName("should return null when no player_list configured")
        void shouldReturnNullWhenMissing() {
            TriggerConfig config = withRaw("connection", Map.of());
            assertNull(config.getPlayerList());
        }

        @Test
        @DisplayName("should parse player list with mode and players")
        void shouldParsePlayerList() {
            TriggerConfig config = withRaw("connection", Map.of("player_list", Map.of(
                "mode", "blacklist",
                "players", List.of("Steve", "Alex")
            )));
            TriggerConfig.PlayerListConfig playerList = config.getPlayerList();
            assertNotNull(playerList);
            assertEquals("blacklist", playerList.getMode());
            assertEquals(List.of("Steve", "Alex"), playerList.getPlayers());
        }

        @Test
        @DisplayName("mode should default to whitelist")
        void modeShouldDefaultToWhitelist() {
            TriggerConfig config = withRaw("connection", Map.of("player_list", Map.of(
                "players", List.of("Steve")
            )));
            assertEquals("whitelist", config.getPlayerList().getMode());
        }
    }

    @Nested
    @DisplayName("ServerListConfig")
    class ServerListConfigTests {

        @Test
        @DisplayName("should return null when no server_list configured")
        void shouldReturnNullWhenMissing() {
            TriggerConfig config = withRaw("connection", Map.of());
            assertNull(config.getServerList());
        }

        @Test
        @DisplayName("should parse server list with mode and servers")
        void shouldParseServerList() {
            TriggerConfig config = withRaw("connection", Map.of("server_list", Map.of(
                "mode", "whitelist",
                "servers", List.of("lobby", "survival")
            )));
            TriggerConfig.ServerListConfig serverList = config.getServerList();
            assertNotNull(serverList);
            assertEquals("whitelist", serverList.getMode());
            assertEquals(List.of("lobby", "survival"), serverList.getServers());
        }
    }

    @Nested
    @DisplayName("VirtualHostListConfig")
    class VirtualHostListConfigTests {

        @Test
        @DisplayName("should return null when no virtual_host_list configured")
        void shouldReturnNullWhenMissing() {
            TriggerConfig config = withRaw("ping", Map.of());
            assertNull(config.getVirtualHostList());
        }

        @Test
        @DisplayName("should parse virtual host list")
        void shouldParseVirtualHostList() {
            TriggerConfig config = withRaw("ping", Map.of("virtual_host_list", Map.of(
                "mode", "whitelist",
                "virtual_hosts", List.of("play.example.com")
            )));
            TriggerConfig.VirtualHostListConfig vhList = config.getVirtualHostList();
            assertNotNull(vhList);
            assertEquals(List.of("play.example.com"), vhList.getVirtualHosts());
        }
    }

    private static TriggerConfig withRaw(String type, Map<String, Object> rawConfig) {
        TriggerConfig config = new TriggerConfig();
        config.setType(type);
        config.setRawConfig(rawConfig);
        return config;
    }
}
