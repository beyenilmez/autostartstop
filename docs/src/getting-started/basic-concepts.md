# Basic Concepts

AutoStartStop uses a **rule-based automation system** to manage your servers. Understanding the core concepts will help you configure the plugin effectively.

## Overview

AutoStartStop works by defining **rules** that react to **triggers** and execute **actions**, optionally filtered by **conditions**. Think of it as:

```{ .text .no-copy }
Trigger → [Condition?] → Action
```

When a trigger fires, conditions are checked (if any), and if they pass, actions are executed.

## Core Components

### Rules

**Rules** are the foundation of AutoStartStop. Each rule defines what should happen when certain events occur.

A rule consists of:

- **Triggers**: When should this rule fire?
- **Conditions** *(optional)*: Should it execute right now?
- **Actions**: What should happen?

```{ .yaml .no-copy }
rules:
  my_rule:
    triggers: [ ... ]    # When to fire
    conditions: { ... }  # Optional: Should it run?
    action: [ ... ]      # What to do
```

See [Rules](/configuration/rules.md) for detailed documentation on rule configuration.

### Triggers

**Triggers** define **when** a rule should fire. They represent events that occur in your server environment.

Common triggers include:

- **`proxy_start`**: When the Velocity proxy starts
- **`connection`**: When a player connects to a server
- **`cron`**: When a scheduled time is reached
- **`manual`**: When a manual trigger is used

A rule can have multiple triggers. The rule fires when **any** of its triggers activate.

```{ .yaml }
rules:
  example:
    triggers:
      - proxy_start:  # Fire on proxy start
      - connection:   # OR fire on player connection
          server: survival
```

See [Triggers](/triggers/index.md) for a complete list of available triggers and their configuration options.

### Conditions

**Conditions** are optional filters that determine **if** a rule should execute. They let you add logic like "only if the server is offline" or "only if player count is greater than 5".

Common conditions include:

- **`server_status`**: Check if a server is online/offline
- **`player_count`**: Check the number of players on a server
- **`string_equals`**: Compare two strings
- **`number_compare`**: Compare numeric values

By default, **all** conditions must pass (AND logic). You can change this to **any** (OR logic).

```{ .yaml }
rules:
  example:
    triggers:
      - connection:
          server: survival
    conditions:
      mode: all  # All conditions must pass
      checks:
        - server_status:
            server: survival
            status: offline  # Only if server is offline
```

!!! tip "Inverting conditions"
    You can invert any condition using `invert: true` to reverse its logic.

See [Conditions](/conditions/index.md) for detailed documentation on all available conditions and their options.

### Actions

**Actions** define **what** should happen when a rule fires. They are executed sequentially in the order they are defined.

Common actions include:


- **`start`**: Start a server
- **`connect`**: Connect a player to a server
- **`send_message`**: Send a message to a player
- **`respond_ping`**: Respond to a ping request with a custom MOTD
- **`if`**: Conditional action execution
- **`sleep`**: Wait for a duration

```{ .yaml }
rules:
  auto_start_on_connection:
    triggers:
      - connection:
          deny_connection: true
          server_list:
            mode: whitelist
            servers:
              - survival
    action:
      - start:
          server: ${connection.server.name}
      - while:
        timeout: 2m
        checks:
          - server_status:
              server: ${connection.server.name}
              status: offline
      - allow_connection:
```

See [Actions](/actions/index.md) for a complete list of available actions and their configuration options.

### Servers

**Servers** define which backend servers AutoStartStop can manage and how to control them.

Each server configuration includes:

- **`virtual_host`**: The hostname used to connect to this server
- **`control_api`**: How to control the server (Shell commands, AMP API, or Pterodactyl API)
- **`ping`**: Settings for checking server status
- **`startup_timer`**: Settings for tracking server startup progress

```{ .yaml }
servers:
  survival:
    virtual_host: play.example.com
    control_api:
      type: shell
      start_command: "systemctl start minecraft-survival"
      stop_command: "systemctl stop minecraft-survival"
```

See [Servers](/configuration/servers.md) for detailed server configuration options, and [Control API](/control-api/index.md) for information about control methods.

### Templates

**Templates** are pre-built rule configurations for common automation patterns. They simplify configuration by providing ready-to-use setups.

Available templates:

- **`start_on_proxy_start`**: Start servers when the proxy starts
- **`stop_on_proxy_shutdown`**: Stop servers when the proxy shuts down
- **`start_on_connection`**: Start a server when a player tries to connect
- **`stop_on_empty`**: Stop a server after it's been empty for a duration
- **`respond_ping`**: Customize ping/MOTD responses

Instead of manually defining triggers, conditions, and actions, you can use a template:

```{ .yaml }
rules:
  auto_start:
    template: 'start_on_connection'
    servers: ['survival']
    mode: waiting_server
    waiting_server:
      server: limbo
```

See [Rule Templates](/rule-templates/index.md) for a complete list of available templates and their configuration options.

## How It All Works Together

Here's a complete example that demonstrates all concepts:

```{ .yaml }
servers:
  survival:  # Define a managed backend server named "survival"
    virtual_host: play.example.com  # Hostname players use to connect
    control_api:
      type: shell  # Use shell commands to control this server
      start_command: "systemctl start minecraft-survival"  # Command to start the server
      stop_command: "systemctl stop minecraft-survival"    # Command to stop the server

rules:
  auto_start_survival:  # Rule for starting "survival" server when a player connects
    triggers:
      - connection:  # Trigger on player connection
          deny_connection: true  # Initially deny connection (until server is ready)
          server_list:
            mode: whitelist  # Only act for specific servers
            servers:
              - survival     # Only "survival" server
    conditions:
      checks:
        - server_status:  # Only run if server is currently offline
            server: survival
            status: offline
    action:
      - start:           # Start the server
          server: survival
      - while:           # Wait until server is online (timeout: 2 minutes)
        timeout: 2m
        checks:
          - server_status:
              server: survival
              status: offline
      - allow_connection:    # Allow the previously denied connection so player can join
          connection: ${connection}
          server: ${connection.server}
```

**What happens:**

1. A player tries to connect to the `survival` server → **Trigger** fires
2. The plugin checks if `survival` is offline → **Condition** is evaluated
3. If the condition passes (server is offline), the **Actions** execute:
    - Start the server
    - Wait until the server is online
    - Allow the player to connect

## Next Steps

Now that you understand the basic concepts, you can:

- Learn how to [configure your first server](/configuration/servers.md)
- Explore available [triggers](/triggers/index.md), [conditions](/conditions/index.md), and [actions](/actions/index.md)
- Use [rule templates](/rule-templates/index.md) for common patterns
- Read the [configuration guide](/configuration/index.md) for detailed options
- Explore [examples](/examples/index.md) of how to use AutoStartStop