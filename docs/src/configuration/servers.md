# Servers

The `servers:` section defines which backend servers AutoStartStop can manage and how to control them.

## Overview

For full functionality, define servers in this plugin's `servers:` section (in `config.yml`). Some plugin features only work when servers are configured here.

## Server configuration structure

```{ .yaml .no-copy }
servers:
  <server_name>:
    virtual_host: <hostname>  # Virtual host
    control_api: { ... }      # Control API configuration
    ping: { ... }             # Ping settings
    startup_timer: { ... }    # Startup timer settings
```

## Control API types

Control APIs define how AutoStartStop manages your backend servers (start, stop, restart operations). The plugin supports three control API types:

- **[Shell](/control-api/shell.md)**: Execute shell commands to control servers
- **[AMP](/control-api/amp.md)**: Use AMP API to control servers
- **[Pterodactyl](/control-api/pterodactyl.md)**: Use Pterodactyl Panel Client API to control servers

For detailed configuration options and examples, see the [Control API](/control-api/index.md) page.

## Virtual host

The `virtual_host` field associates a server with a specific hostname or IP address. This is primarily used for MOTD caching and ping response customization.

```{ .yaml }
servers:
  survival:
    virtual_host: play.example.com  # Virtual host for this server
```

- **`virtual_host`**: The hostname or IP address that clients use to connect to this server

This is used as a key to cache the server's MOTD. This allows the [`respond_ping`](/actions/ping-management/respond-ping.md) action to display the cached MOTD even when the server is offline.

!!! tip "MOTD cache requirement"
    To use `use_cached_motd: true` in the [`respond_ping`](/actions/ping-management/respond-ping.md) action, you must configure `virtual_host` in the server configuration. The cached MOTD is retrieved based on the client's virtual host from the ping request.

## Ping settings

Ping configuration for checking server status (online/offline).

```{ .yaml }
servers:
  lobby:
    ping:
      timeout: 30s
      method: velocity
```

- **`timeout`**: Ping timeout when checking server status (default: `30s`)
- **`method`**: Method to check server status:
    - `velocity`: Uses Velocity's built-in ping (default, works with all servers)
    - `control_api`: Uses the control API to check status (supported by AMP and Pterodactyl, falls back to `velocity` if not supported)

## Startup timer settings

Startup timer configuration for tracking server startup progress. These settings are used to calculate the server startup progress to show to users.

```{ .yaml }
servers:
  survival:
    startup_timer:
      expected_startup_time: 30s
      auto_calculate_expected_startup_time: false
```

- **`expected_startup_time`**: Expected startup time for progress calculations (default: `30s`)
- **`auto_calculate_expected_startup_time`**: Automatically calculate expected startup time from historical data (default: `false`)
