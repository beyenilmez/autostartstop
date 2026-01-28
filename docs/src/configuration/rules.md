# Rules

Rules execute **actions** based on **triggers**, optionally filtered by **conditions**. They define what should happen when certain events occur.

## Rule structure

```{ .yaml .no-copy }
rules:
  <rule_name>:
    enabled: true        # Optional: Enable/disable rule (default: true)
    triggers: [ ... ]    # What events should fire this rule
    conditions: { ... }  # Optional: Should the action execute
    action: [ ... ]      # What actions to perform
```

Or use a template:

```{ .yaml .no-copy }
rules:
  <rule_name>:
    enabled: true              # Optional: Enable/disable rule (default: true)
    template: <template_name>  # Use a built-in template
    # ... template-specific configuration
```

## Rule components

### Triggers

Triggers define **when** a rule should fire. A rule can have multiple triggers, and the rule fires when any trigger activates.

Available triggers:

| Trigger | Description |
|---------|-------------|
| `proxy_start` | Fires when the proxy starts |
| `proxy_shutdown` | Fires when the proxy shuts down |
| `connection` | Fires when a player connects to a server |
| `empty_server` | Fires when a server has been empty for a duration |
| `cron` | Fires when a cron expression is matched |
| `ping` | Fires when a client pings the proxy (server list/MOTD requests) |
| `manual` | Fires via `/autostartstop trigger` command |

See the [Triggers](/triggers/index.md) section for detailed documentation on each trigger type.

### Conditions

Conditions define **if** a rule should execute (optional). They filter triggers to ensure actions only run under specific circumstances.

Available conditions:

| Condition | Description |
|-----------|-------------|
| `server_status` | Check if server is online/offline |
| `player_count` | Check player count on a server |
| `string_equals` | Compare two strings |
| `number_compare` | Compare numeric values |

#### Condition evaluation mode

Conditions support two evaluation modes:

- **`all`** (default): All conditions must be `true` for the rule to execute (AND logic)
- **`any`**: At least one condition must be `true` for the rule to execute (OR logic)

!!! tip "Inverting conditions"
    All conditions support the `invert` parameter to negate the result. Set `invert: true` to reverse the condition's logic.

See the [Conditions](/conditions/index.md) section for detailed documentation.

### Actions

Actions define **what** should happen when a rule fires.

Available actions:

| Action | Description |
|--------|-------------|
| `start` | Start a server |
| `stop` | Stop a server |
| `restart` | Restart a server |
| `send_command` | Send a command to a server |
| `connect` | Connect a player to a server |
| `disconnect` | Disconnect one or more players from the proxy |
| `allow_connection` | Allow a player to connect to a server |
| `send_message` | Send a message to a player |
| `send_action_bar` | Send an action bar message to a player |
| `send_title` | Send a title to a player |
| `clear_title` | Clear a player's title |
| `show_bossbar` | Show a bossbar to a player |
| `hide_bossbar` | Hide a bossbar from a player |
| `deny_ping` | Deny a ping request |
| `allow_ping` | Allow a ping request to proceed normally |
| `respond_ping` | Respond to a ping request with a custom MOTD |
| `if` | Conditional action execution |
| `while` | Loop while a condition is true |
| `sleep` | Wait for a duration |
| `log` | Log a message to the console |
| `exec` | Execute a shell command |

See the [Actions](/actions/index.md) section for detailed documentation.

## Example rules

### Simple manual trigger

```{ .yaml }
rules:
  # Triggered by: /autostartstop trigger start_server <server_name>
  manual_start:
    triggers:
      - manual:
          id: 'start_server'
    action:
      - start:
          server: ${manual.args.0}  # Server name from command argument
```

### Stop on proxy shutdown

```{ .yaml }
rules:
  stop_on_shutdown:
    triggers:
      - proxy_shutdown:
    action:
      - stop:
          server: lobby
```

### Scheduled task (cron)

```{ .yaml }
rules:
  nightly_restart:
    triggers:
      - cron:
          expression: '0 3 * * *'  # 3 AM daily
    action:
      - send_message:
          server: survival
          message: "<yellow>Server will restart in 1 minute.</yellow>"
      - sleep:
          duration: 1m
      - restart:
          server: survival
```

## Rule templates

Instead of defining triggers, conditions, and actions manually, you can use built-in templates for common patterns. Templates simplify configuration for frequently used automation scenarios.

Available templates:

| Template | Description |
|----------|-------------|
| `start_on_proxy_start` | Start servers when the proxy starts |
| `stop_on_proxy_shutdown` | Stop servers when the proxy shuts down |
| `start_on_connection` | Start a server when a player tries to connect |
| `stop_on_empty` | Stop servers after they've been empty for a duration |
| `respond_ping` | Customize ping/MOTD responses based on server status |

Example:
```{ .yaml }
rules:
  start_on_connect:
    template: 'start_on_connection'
    servers: ['survival']
    mode: waiting_server
    waiting_server:
      server: limbo
      start_waiting_server_on_connection: true
```

See the [Templates](/rule-templates/index.md) section for detailed configuration options.
