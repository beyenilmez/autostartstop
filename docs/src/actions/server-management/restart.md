# restart Action

Restarts a server using the configured control API.

!!! requirement "Control API required"
    All server management actions require:

    - Server configuration in the [`servers:`](/configuration/servers.md) section of `config.yml`
    - A configured [control API](/control-api/index.md)
    
    See [Servers](/configuration/servers.md) for configuration details.

!!! note "Shell Control API"
    When using the [Shell Control API](/control-api/shell.md), the `restart_command` is required in the server's control API configuration.

!!! tip "MOTD caching"
    Before restarting a server, AutoStartStop automatically caches the server's MOTD (Message of the Day). This cached MOTD can be displayed when the server is offline using the [`respond_ping`](/actions/ping-management/respond-ping.md) action with `use_cached_motd: true`.

## Configuration

```{ .yaml }
action:
  - restart:
      server: survival  # Required: Server name
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | The server name to restart |

## How it works

When the `restart` action is executed, AutoStartStop caches the server's MOTD (while the server is still online) and sends the restart command to the server using the configured [Control API](/control-api/index.md).

## Example

```{ .yaml }
rules:
  nightly_restart:
    triggers:
      - cron:
          expression: '0 3 * * *'  # Every day at 3 AM
    action:
      - send_message:
          server: survival
          message: "<yellow>Server will restart in 1 minute.</yellow>"  # Notify players
      - sleep:
          duration: 1m  # Wait 1 minute
      - restart:
          server: survival  # Restart the server
```
This rule will notify players 1 minute before restarting the server, then restart it at the scheduled time.