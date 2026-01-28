# start Action

Starts a server using the configured control API.

!!! requirement "Control API required"
    All server management actions require:

    - Server configuration in the [`servers:`](/configuration/servers.md) section of `config.yml`
    - A configured [control API](/control-api/index.md)
    
    See [Servers](/configuration/servers.md) for configuration details.

!!! note "Shell Control API"
    When using the [Shell Control API](/control-api/shell.md), the `start_command` is required in the server's control API configuration.

## Configuration

```{ .yaml }
action:
  - start:
      server: survival  # Required: Server name
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | The server name to start |

## How it works

When the `start` action is executed, AutoStartStop sends the start command to the server using the configured [Control API](/control-api/index.md) and automatically begins tracking the startup process.

The startup tracker monitors the server state in the background (polls every second) and calculates progress based on elapsed time and the expected startup time configured in the server's [`startup_timer`](/configuration/servers.md#startup-timer-settings) setting. Tracking stops when the server comes online or after a timeout (10 minutes).

### Startup progress variables

The following variables become available globally for tracking startup progress:

| Variable | Description |
|----------|-------------|
| `<server_name>.state` | Current server state: `unknown`, `offline`, `starting`, `stopping`, `restarting`, `online`, `failed` (Not all control APIs support all states) |
| `<server_name>.startup_timer` | Seconds elapsed since startup began |
| `<server_name>.startup_progress` | Startup progress as a decimal (0.0-1.0), useful for bossbars |
| `<server_name>.startup_progress_percentage` | Startup progress as a percentage (0-100) |

Progress increases from 0% to 99% as time passes, reaching 100% only when the server comes online.

## Example

```{ .yaml }
rules:
  auto_start_on_connection:
    triggers:
      - connection:
          deny_connection: true
          server_list:
            mode: whitelist
            servers:
              - survival  # Only trigger for connections to survival server
    action:
      - start:
          server: ${connection.server.name}  # Start the server that player is trying to connect to
      - while:  # Wait for server to start
        timeout: 2m
        checks:
          - server_status:
              server: ${connection.server.name}
              status: offline
      - allow_connection:  # Allow the connection after server is ready
```
This rule will automatically start the server when a player tries to connect to it, wait for it to start, and then allow the connection.