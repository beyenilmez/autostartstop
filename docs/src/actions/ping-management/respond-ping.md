# respond_ping Action

Responds to a ping request with custom MOTD, version, and icon. Use this action when a ping trigger has `hold_response: true` and you want to respond to the ping request with custom ping data. See [ping](/triggers/ping.md) for more information.

Only modifies the ping response for the fields specified in the action configuration. Any fields not specified will use the existing ping response for that field.

## Configuration

```{ .yaml }
action:
  - respond_ping:
      ping: ${ping}                         # Ping event (default: ${ping})
      use_cached_motd: true                 # Use cached MOTD if available
      use_backend_motd: false               # Use live backend MOTD (pings `${ping.server}`)
      motd: "<yellow>Custom MOTD</yellow>"  # Custom MOTD (MiniMessage format)
      version_name: "Custom Version"        # Version name
      protocol_version: -1                  # Protocol version
      icon: "/path/to/icon.png"             # Server icon (64x64 PNG)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `ping` | `${ping}` | The ping event object |
| `use_cached_motd` | `false` | If `true`, uses cached MOTD if available |
| `use_backend_motd` | `false` | If `true`, uses live backend MOTD (pings `${ping.server}`). Requires `ping.server` to be set (done by [ping](/triggers/ping.md) trigger). |
| `motd` | - | Custom MOTD in MiniMessage format. Used when cached and backend MOTD are not used. Supports variables. |
| `version_name` | - | Version name to display. Supports variables. |
| `protocol_version` | - | Protocol version (use `-1` to show version name text to clients) |
| `icon` | - | Server icon file path or base64 string (must be 64x64 PNG) |

!!! important "MOTD cache and virtual hosts"
    To use `use_cached_motd: true`, you must configure `virtual_host` in the server configuration. The MOTD cache is organized by virtual host, and the cached MOTD is retrieved based on the client's virtual host from the ping request.
    
    **Server configuration:**
    
    ```{ .yaml }
    servers:
      survival:
        virtual_host: play.example.com  # Virtual host for this server
    ```
        
    **How it works:**
    
    1. MOTD is cached automatically when:
       
        - A server is stopped by the plugin (via [`stop`](/actions/server-management/stop.md) or [`restart`](/actions/server-management/restart.md) actions)
        - Periodically at the interval specified in [`motd_cache_interval`](/configuration/settings.md#motd_cache_interval) setting
    
    2. When `use_cached_motd: true` is used, the action retrieves the cached MOTD based on the client's virtual host from the ping request (`${ping.player.virtual_host}`).
    
    3. MOTD priority: **cached** (if `use_cached_motd` and cache hit) → **backend** (if `use_backend_motd` and ping succeeds) → **motd** (custom). 
    
    See [Servers](/configuration/servers.md#virtual-host) for server configuration details and [Settings](/configuration/settings.md#motd_cache_interval) for MOTD cache interval configuration.

## Example

```{ .yaml }
rules:
  respond_ping:
    triggers:
      - ping:
          hold_response: true  # Hold the ping response
    conditions:
      mode: all
      checks:
        - server_status:
            server: survival
            status: offline  # Only respond if server is offline
    action:
      - respond_ping:
          ping: ${ping}  # Ping event
          use_cached_motd: true  # Use cached MOTD
          protocol_version: -1  # Force version name display as text
          version_name: "<blue>◉ Sleeping"  # Version name
```
This rule will respond to ping requests with a cached MOTD and custom version name when the server is offline.
