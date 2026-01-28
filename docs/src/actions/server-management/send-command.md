# send_command Action

Sends a command to a server's console using the configured control API.

!!! requirement "Control API required"
    All server management actions require:

    - Server configuration in the [`servers:`](/configuration/servers.md) section of `config.yml`
    - A configured [control API](/control-api/index.md)
    
    See [Servers](/configuration/servers.md) for configuration details.

!!! warning "Shell Control API Support"
    When using the [Shell Control API](/control-api/shell.md), the `send_command_command` is required in the server's control API configuration.

## Configuration

```{ .yaml }
action:
  - send_command:
      server: survival  # Server name
      command: "say Welcome to the server!"  # Command to send
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | The server name to send the command to |
| `command` | - | The command to send to the server console |

## How it works

When the `send_command` action is executed, AutoStartStop sends the command to the server's console using the configured [Control API](/control-api/index.md).

## Example

```{ .yaml }
rules:
  give_diamond_every_hour:
    triggers:
      - cron:
          expression: '0 * * * *'
    actions:
      - send_command:
          server: survival
          command: "give @r diamond"
```
This rule will give a diamond to a random player in the survival server every hour.