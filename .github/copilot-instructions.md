# Copilot Coding Agent Instructions for AutoStartStop

## Project Overview

AutoStartStop is a **Velocity proxy plugin** (Minecraft) for automated server management using a rule-based system. Players define **rules** with **triggers** (events), optional **conditions**, and **actions** to automate starting/stopping backend servers, managing player connections, and more.

- **Language**: Java 21
- **Build system**: Gradle 9.x with Kotlin DSL (`build.gradle.kts`)
- **Dependency management**: Gradle version catalog at `gradle/libs.versions.toml`
- **Target platform**: Velocity 3.4.0+ proxy server
- **License**: GPL-3.0
- **Documentation site**: MkDocs-based, in `docs/` directory

## Build & Test Commands

```bash
# Build the project (compiles + runs tests + creates shadow JAR)
./gradlew build

# Run tests only
./gradlew test

# Clean build artifacts
./gradlew clean

# Build without tests
./gradlew build -x test
```

The build produces a shadow JAR at `build/libs/AutoStartStop-<version>.jar`.

## Project Structure

```
src/main/java/com/autostartstop/
├── AutoStartStop.java          # Main plugin entry point (@Plugin annotation)
├── Log.java                    # Logging utility
├── PluginLogger.java           # Logger implementation
├── action/                     # Action system (interfaces + registry)
│   ├── impl/                   # Concrete action implementations (22 actions)
│   ├── Action.java             # Action interface
│   ├── ActionType.java         # Enum registering all action types
│   ├── ActionRegistry.java     # Registry for action creators
│   ├── ActionCreator.java      # Functional interface for creating actions
│   └── ActionContext.java      # Context passed to action creators (builder pattern)
├── trigger/                    # Trigger system (same pattern as actions)
│   ├── impl/                   # 7 trigger implementations
│   ├── TriggerType.java        # Enum registering all trigger types
│   └── TriggerRegistry.java    # Registry for trigger creators
├── condition/                  # Condition system (same pattern)
│   ├── impl/                   # 4 condition implementations
│   ├── ConditionType.java      # Enum registering all condition types
│   └── ConditionEvaluator.java # Evaluates conditions with inversion support
├── template/                   # Pre-built rule templates
│   ├── impl/                   # 5 template implementations
│   └── TemplateType.java       # Enum registering all template types
├── api/                        # Server control API abstraction
│   ├── impl/                   # Shell, AMP, Pterodactyl implementations
│   └── ServerControlApiType.java
├── config/                     # YAML configuration parsing and validation
├── context/                    # ExecutionContext and VariableResolver
├── rule/                       # Rule execution engine (RuleManager, RuleExecutor)
├── server/                     # Server management (ManagedServer, ServerManager)
├── command/                    # Plugin commands (/autostartstop reload, trigger)
├── update/                     # Update checker (GitHub releases)
├── metrics/                    # bStats metrics
└── util/                       # Utilities (DurationUtil, MiniMessageUtil, etc.)

src/test/java/com/autostartstop/  # Tests mirror the main source structure
src/main/resources/config.yml     # Default configuration template
docs/                             # MkDocs documentation site source
```

## Architecture & Design Patterns

### Registry + Enum Factory Pattern (core pattern used throughout)

All extensible types (actions, triggers, conditions, templates, control APIs) follow the same pattern:

1. **Interface** — defines the contract (e.g., `Action`, `Trigger`, `Condition`)
2. **Functional creator interface** — e.g., `ActionCreator` takes `(ActionConfig, ActionContext) -> Action`
3. **Enum with creators** — e.g., `ActionType` stores config name + creator reference: `LOG("log", LogAction::create)`
4. **Static `create()` factory method** — each `impl/` class has a static method for construction
5. **Registry** — `ActionRegistry` manages the mapping from config name to creator

### Adding a New Action / Trigger / Condition / Template

1. Create the implementation class in the appropriate `impl/` directory
2. Add a static `create(Config, Context)` factory method
3. Register it in the corresponding enum (e.g., add an entry to `ActionType`)

The registry will automatically discover it via `ActionType.fromConfigName()`.

### Context Builder Pattern

`ActionContext`, `ConditionContext`, `TriggerContext`, and `TemplateContext` use a builder pattern for construction:
```java
ActionContext.builder()
    .serverManager(serverManager)
    .variableResolver(variableResolver)
    // ... more fields
    .build();
```

### Configuration

Configuration is YAML-based using the BoostedYAML library. Config classes are in `com.autostartstop.config`:
- `PluginConfig` — root config
- `ServerConfig`, `RuleConfig`, `ActionConfig`, `TriggerConfig`, `ConditionConfig` — sub-configs
- `ConfigParser` — parses YAML sections into config objects
- `ConfigMerger` — merges server defaults with individual server configs
- `ConfigAccessor` — safe accessor for YAML values with type checking

## Testing

- **Framework**: JUnit 5 with Mockito
- **Pattern**: Tests use `@ExtendWith(MockitoExtension.class)`, `@Mock` for dependencies, `@Nested` + `@DisplayName` for hierarchical grouping
- **Test focus**: Registry behavior, factory `create()` validation (including `ConfigException` on bad input), condition evaluation, variable resolution
- **Test location**: `src/test/java/com/autostartstop/` mirroring main source structure
- **Run tests**: `./gradlew test`

Example test pattern for a new condition:
```java
@ExtendWith(MockitoExtension.class)
class MyConditionTest {
    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("throws ConfigException when required field is missing")
        void throwsOnMissingField() {
            // ...
        }
    }

    @Nested
    @DisplayName("evaluate")
    class Evaluate {
        @Test
        @DisplayName("returns true when condition is met")
        void returnsTrueWhenMet() {
            // ...
        }
    }
}
```

## CI / Workflows

### `ci.yml` — Build & Release
- Triggers on push/PR to `main` (excluding `docs/`, `*.md`, `LICENSE`, `.github/**` except `ci.yml` itself)
- Jobs: Extract version → Build with Gradle → (optional) GitHub draft release
- Java 21, Temurin distribution
- Auto-creates draft releases when version is bumped in `build.gradle.kts`

### `docs.yml` — Documentation Deployment
- Triggers on push/PR to `main` when `docs/` changes
- Python + MkDocs build → GitHub Pages deployment

### `labeler.yml` — PR Auto-labeling
- Labels PRs based on branch name prefixes (`feat/`, `fix/`, `docs/`, etc.)

## Key Files to Know

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Build config, dependencies, shadow JAR setup |
| `gradle/libs.versions.toml` | Version catalog for all dependencies |
| `gradle.properties` | Gradle JVM args, caching, parallel execution |
| `src/main/resources/config.yml` | Default plugin configuration template |
| `.github/workflows/ci.yml` | CI build & release workflow |
| `.github/labeler.yml` | PR label rules by branch prefix |
| `.github/release.yml` | GitHub release notes categories |
| `.github/renovate.json` | Renovate bot config for dependency updates |

## Version Management

The plugin version is defined in `build.gradle.kts`:
```kotlin
version = "1.1.0-beta"
```
It must also match the `@Plugin` annotation in `AutoStartStop.java`. The CI workflow extracts the version from `build.gradle.kts` and creates a draft release when the version tag doesn't exist yet.

## Common Pitfalls & Workarounds

1. **Java toolchain**: The project requires Java 21. Ensure `java.toolchain.languageVersion` is set to 21 in `build.gradle.kts` and matches `gradle/libs.versions.toml` `[versions] java = "21"`.

2. **Shadow JAR conflicts**: Dependencies are relocated in `build.gradle.kts` (`relocate` blocks) to avoid classpath conflicts with other Velocity plugins. When adding a new dependency, consider if it needs relocation.

3. **Velocity API is `compileOnly`**: The Velocity API is provided at runtime by the proxy server. It's `compileOnly` in the main source set but `testImplementation` in tests so Adventure components (MiniMessage, etc.) are available for testing.

4. **No code formatter/linter configured**: The project does not use Checkstyle, Spotless, or similar tools. Follow the existing code style: 4-space indentation, opening brace on same line, descriptive method and variable names.

5. **CI is not triggered by `.github/` changes** (except `ci.yml` itself) or `docs/` or `*.md` files. If you modify only these paths, CI will not run.

6. **Configuration cache**: Gradle configuration cache is enabled (`org.gradle.configuration-cache=true` in `gradle.properties`). If you encounter stale build issues, run `./gradlew clean` or `./gradlew --no-configuration-cache build`.

## Documentation

- Full docs: https://beyenilmez.github.io/autostartstop/
- LLM-friendly docs: https://beyenilmez.github.io/autostartstop/llms-full.txt
- Docs source: `docs/src/` (MkDocs with Material theme)
- Docs config: `docs/mkdocs.yml`
