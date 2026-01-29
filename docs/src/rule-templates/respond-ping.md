# respond_ping Template

Customizes ping/MOTD responses based on server status. This allows you to show different information when a server is online vs. offline, such as showing a "sleeping" status for offline servers.

## Configuration

```{ .yaml }
rules:
  custom_ping:
    template: respond_ping
    virtual_hosts:  # List of virtual hosts to handle
      - play.example.com
      - mc.example.com
    servers:        # List of server names (maps to virtual_hosts from config)
      - survival
      - creative
    offline:        # Configuration for when server is offline
      use_cached_motd: true
      use_backend_motd: false
      motd: ""
      version_name: "<blue>◉ Sleeping"
      protocol_version: -1
      icon: "/path/to/offline-icon.png"
    online:         # Configuration for when server is online
      use_cached_motd: false
      use_backend_motd: false
      motd: ""
      version_name: ""
      protocol_version: -1
      icon: "/path/to/online-icon.png"
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `virtual_hosts` | - | List of virtual hosts to handle. At least one of `virtual_hosts` or `servers` must be specified. |
| `servers` | - | List of server names to handle. Maps server names to their `virtual_host` values from the `servers:` configuration. Check [servers](/configuration/servers.md/#virtual-host) for more information. At least one of `virtual_hosts` or `servers` must be specified. |
| `offline` | - | Configuration for when the server is offline. At least one of `offline` or `online` must be specified. |
| `online` | - | Configuration for when the server is online. At least one of `offline` or `online` must be specified. |

### Offline/Online configuration

Both `offline` and `online` sections support the same fields:

| Field | Default | Description |
|-------|---------|-------------|
| `use_cached_motd` | false | If `true`, uses the cached MOTD from when the server was last online. If `false` or not specified, uses the `motd` field. |
| `use_backend_motd` | false | If `true`, uses the live backend server MOTD. |
| `motd` | - | Custom MOTD text (MiniMessage format). Used when cached and backend MOTD are not used. Supports variables. |
| `version_name` | - | Custom version name text (MiniMessage format). Use with `protocol_version: -1` to show custom text instead of version. Supports variables. |
| `protocol_version` | - | Protocol version number. Use `-1` to show `version_name` as text instead of a version number. |
| `icon` | - | Server icon. Can be a file path or base64-encoded png image string. Must be 64x64 pixels. |

!!! note "At least one config required"
    You must specify at least one of `offline` or `online`. If you only specify one, the template will only modify respond to pings when the server is in that state.

!!! note
    Any field can be omitted, and the template will not modify the existing ping response for that field.

## How it works

The `respond_ping` template:

1. Uses the [`ping`](/triggers/ping.md) trigger internally with `hold_response: true`
2. Finds the server associated with the virtual host
3. Checks if the server is online or offline
4. Selects the appropriate configuration based on server status
5. Uses the [`respond_ping`](/actions/ping-management/respond-ping.md) action to send the custom response

The template matches virtual hosts by comparing them (case-insensitive) with the `virtual_host` configured in each server's configuration. Check [servers](/configuration/servers.md/#virtual-host) for more information.

## Examples

### Show "sleeping" status for offline servers

```{ .yaml }
rules:
  sleeping_server:
    template: respond_ping
    servers: ['survival']
    offline:
      use_cached_motd: true
      version_name: "<blue>◉ Sleeping"
      protocol_version: -1
      icon: icons/sleeping-icon.png
```

When the server is offline, it will show "◉ Sleeping" as the version name and use the cached MOTD. When online, it uses the default server response.

### Custom MOTD for both states

```{ .yaml }
rules:
  custom_motd:
    template: respond_ping
    virtual_hosts: ['survival.example.com']
    offline:
      motd: "<red>Server is offline</red>\n<gray>Check back later!</gray>"
      version_name: "<red>Offline</red>"
      protocol_version: -1
    online:
      motd: "<green>Welcome to Survival!</green>\n<yellow>Join now!</yellow>"
      version_name: "<green>Online</green>"
      protocol_version: -1
```

### Different icons for online/offline

```{ .yaml }
rules:
  custom_icons:
    template: respond_ping
    virtual_hosts: ['play.example.com']
    offline:
      icon: "/icons/offline.png"
    online:
      icon: "/icons/online.png"
```

### Online-only configuration

```{ .yaml }
rules:
  online_only:
    template: respond_ping
    virtual_hosts: ['play.example.com']
    online:
      motd: "<green>Welcome!</green>"
      version_name: "<green>Online</green>"
      protocol_version: -1
```

This will only customize the ping response when the server is online. When offline, the ping will proceed normally (no custom response).
