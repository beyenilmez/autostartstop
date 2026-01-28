# stop Action

Stops a server using the configured control API.

!!! requirement "Control API required"
    All server management actions require:

    - Server configuration in the [`servers:`](/configuration/servers.md) section of `config.yml`
    - A configured [control API](/control-api/index.md)
    
    See [Servers](/configuration/servers.md) for configuration details.

!!! note "Shell Control API"
    When using the [Shell Control API](/control-api/shell.md), the `stop_command` is required in the server's control API configuration.

!!! tip "MOTD caching"
    Before stopping a server, AutoStartStop automatically caches the server's MOTD (Message of the Day). This cached MOTD can be displayed when the server is offline using the [`respond_ping`](/actions/ping-management/respond-ping.md) action with `use_cached_motd: true`.

## Configuration

```{ .yaml }
action:
  - stop:
      server: survival  # Required: Server name
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | The server name to stop |

## How it works

When the `stop` action is executed, AutoStartStop caches the server's MOTD (while the server is still online) and sends the stop command to the server using the configured [Control API](/control-api/index.md).

## Example

```{ .yaml }
rules:
  stop_empty:
    triggers:
      - empty_server:
          empty_time: 15m  # Trigger after server is empty for 15 minutes
          server_list:
            mode: whitelist
            servers:
              - survival  # Only monitor survival server
    action:
      - stop:
          server: survival  # Stop the empty server
```
This rule will automatically stop the server after it has been empty for 15 minutes.