# Variables

Variables allow you to use dynamic values in actions and conditions. They are resolved at runtime and can reference context data from triggers, server states, and more.

## Variable syntax

Variables use the `${variable_name}` syntax and can be used in any string field in your configuration:

```{ .yaml }
action:
  - allow_connection:
      connection: ${connection}  # Use variable for connection
      server: ${connection.server}  # Use variable for server
  - log:
      message: "<yellow><bold>${connection.player.name} connected</bold></yellow>"  # Use variable in message
```

## Variable types

AutoStartStop supports two main types of variables:

1. **Context variables**: Provided by triggers and available during rule execution
2. **Global server variables**: Access server state and properties from anywhere

## Context variables

Context variables are provided by triggers when they fire. Each trigger type provides its own set of variables.

### Common context variables

All rule executions provide the following common context variables:

| Variable | Description |
|----------|-------------|
| `_rule_name` | The name of the rule being executed (as defined in `rules:` section) |
| `_trigger_type` | The type of trigger that fired (e.g., `proxy_start`, `connection`, `cron`, `manual`) |

These variables are useful for:

- **`_rule_name`**: Identifying which rule is executing, useful for logging and debugging
- **`_trigger_type`**: Determining which trigger activated the rule, especially useful when a rule has multiple triggers

### Trigger-specific variables

Each trigger type provides its own set of context variables. For detailed information about variables available from each trigger, see the individual trigger documentation:

- **[proxy_start](/triggers/proxy-start.md#context-variables)**: No additional variables (only `_trigger_type`)
- **[proxy_shutdown](/triggers/proxy-shutdown.md#context-variables)**: No additional variables (only `_trigger_type`)
- **[connection](/triggers/connection.md#context-variables)**: Player and server information from connection events
- **[empty_server](/triggers/empty-server.md#context-variables)**: Server information and empty time data
- **[cron](/triggers/cron.md#context-variables)**: Cron expression, format, timezone, and execution times
- **[ping](/triggers/ping.md#context-variables)**: Server and client information from ping requests
- **[manual](/triggers/manual.md#context-variables)**: Command arguments and trigger ID



## Global server variables

Global server variables allow you to access server state and properties from anywhere in your configuration. They use the format `${server-name.property}`.

### Available server properties

| Property | Description | Type |
|----------|-------------|------|
| `.name` | Server name | String |
| `.status` | Server status (`online` or `offline`) | String |
| `.state` | Detailed server state (`unknown`, `offline`, `starting`, `stopping`, `restarting`, `online`, `failed`) | String |
| `.player_count` | Number of players on the server | Integer |
| `.players` | Collection of Player objects on the server | Collection |
| `.startup_timer` | Seconds elapsed since startup began | Long |
| `.startup_progress` | Startup progress as a decimal (0.0-1.0) | Double |
| `.startup_progress_percentage` | Startup progress as a percentage (0-100) | Integer |

!!! note "Server state vs status"
    - `status`: Simple binary state (`online` or `offline`)
    - `state`: Detailed state that includes transitional states like `starting`, `stopping`, `restarting`
    
    Not all control APIs support all states. If a control API doesn't support a state, it will fall back to `online` or `offline`.

### Examples

**Get player count:**

```{ .yaml }
action:
  - send_message:
      server: survival
      message: "<green>Players online: ${survival.player_count}</green>"
```

**Use server name directly:**

```{ .yaml }
action:
  - start:
      server: ${survival.name}  # Returns "survival"
```

**Access server players:**

```{ .yaml }
action:
  - connect:
      server: target
      players: ${survival.players}  # All players from survival server
```

**Startup progress tracking:**

```{ .yaml }
action:
  - show_bossbar:
      server: survival
      id: "startup"
      message: "<yellow>Server starting... ${survival.startup_progress_percentage}%</yellow>"
      progress: ${survival.startup_progress}  # 0.0 to 1.0
```

## Nested variables

Variables can be nested to access properties of objects. For example:

```{ .yaml }
action:
  - connect:
      server: ${manual.args.1}  # Target server from command argument
      players: ${${manual.args.0}.players}  # Players from source server (nested variable)
```

In this example:

1. `${manual.args.0}` resolves to a server name (e.g., `"survival"`)
2. The outer `${...}` then resolves `${survival.players}` to get all players from that server

## Variable resolution

Variables are resolved in the following order:

1. **Context variables**: Variables set by the trigger that fired the rule
2. **Global server variables**: Server properties accessed via `${server-name.property}`

If a variable can not be resolved, it will remain as-is in the string (e.g., `${unknown_variable}` will not be replaced).

!!! tip "Variable resolution passes"
    Variable resolution supports up to 5 passes to handle nested variables. This allows complex expressions like `${${manual.args.0}.players}` to work correctly.

## Common use cases

### Dynamic server selection

```{ .yaml }
rules:
  auto_start:
    triggers:
      - connection:
          server_list:
            mode: whitelist
            servers:
              - survival
              - creative
    action:
      - start:
          server: ${connection.server.name}  # Start the server the player is trying to connect to
```

### Player information

```{ .yaml }
rules:
  log_connection:
    triggers:
      - connection:
    action:
      - log:
          message: "Player ${connection.player.name} (${connection.player.uuid}) connected to ${connection.server.name}"
```

### Conditional logic based on trigger type

```{ .yaml }
rules:
  multi_trigger:
    triggers:
      - manual:
          id: 'test'
      - cron:
          expression: '0 * * * *'
    action:
      - if:
          mode: all
          checks:
            - string_equals:
                value: ${_trigger_type}
                equals: manual
          then:
            - log:
                message: "Manual trigger executed"
          else:
            - log:
                message: "Cron trigger executed"
```