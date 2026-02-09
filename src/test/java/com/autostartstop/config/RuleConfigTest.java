package com.autostartstop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleConfig")
class RuleConfigTest {

    @Nested
    @DisplayName("isEnabled()")
    class EnabledTests {

        @Test
        @DisplayName("should default to true when enabled is never set")
        void shouldDefaultToTrueWhenNeverSet() {
            RuleConfig config = new RuleConfig();
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("should default to true when set to null explicitly")
        void shouldDefaultToTrueWhenNull() {
            RuleConfig config = new RuleConfig();
            config.setEnabled(null);
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("should respect false when set explicitly")
        void shouldRespectFalse() {
            RuleConfig config = new RuleConfig();
            config.setEnabled(false);
            assertFalse(config.isEnabled());
        }
    }

    @Nested
    @DisplayName("isTemplateRule()")
    class TemplateTests {

        @Test
        @DisplayName("should not be a template rule by default")
        void shouldNotBeTemplateByDefault() {
            RuleConfig config = new RuleConfig();
            assertFalse(config.isTemplateRule());
        }

        @Test
        @DisplayName("should be a template rule when template name is set")
        void shouldBeTemplateWhenSet() {
            RuleConfig config = new RuleConfig();
            config.setTemplate("start_on_connection");
            assertTrue(config.isTemplateRule());
        }

        @Test
        @DisplayName("should not be a template rule when template is empty string")
        void shouldNotBeTemplateForEmpty() {
            RuleConfig config = new RuleConfig();
            config.setTemplate("");
            assertFalse(config.isTemplateRule());
        }
    }
}
