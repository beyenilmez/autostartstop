# Configuration

AutoStartStop uses a single YAML configuration file located at:

```{ .text .no-copy }
plugins/autostartstop/config.yml
```

The configuration file is automatically created on first plugin startup with default values.

## Configuration structure

The `config.yml` file consists of several main sections:

```{ .yaml .no-copy }
version: 1         # Configuration version (do not modify)

settings: { ... }  # Global plugin settings
defaults: { ... }  # Default values for server configurations
servers: { ... }   # Server definitions and control API settings
rules: { ... }     # Automation rules (triggers, conditions, actions)
```

## Reloading configuration

After editing `config.yml`, reload the configuration using:

```
/autostartstop reload
```

Or use the alias:

```
/ass reload
```

Alternatively, you can restart Velocity to load the new configuration.

For detailed information about all available commands, see [Commands](/commands.md).

## Configuration sections

- **[Settings](settings.md)**: Global plugin settings (timeouts, intervals, etc.)
- **[Defaults](defaults.md)**: Default values merged into all server configurations
- **[Servers](servers.md)**: Define which servers to manage and how to control them (Shell, AMP, or Pterodactyl API)
- **[Rules](rules.md)**: Automation rules that execute actions based on triggers and conditions

## Next Steps

- **[Examples](/examples/index.md)**: Example configurations for common use cases