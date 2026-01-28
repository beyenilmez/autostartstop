# ping Trigger

Fires when a client pings the proxy server (server list/MOTD requests).

## Configuration

```{ .yaml }
triggers:
  - ping:
      virtual_host_list:    # Optional: Filter by virtual host
        mode: whitelist     # 'whitelist' or 'blacklist'
        virtual_hosts:
          - example.com
          - mc.example.com
          - 192.168.1.100
      server_list:          # Optional: Filter by server names (maps to virtual_hosts from config)
        mode: whitelist     # 'whitelist' or 'blacklist'
        servers:
          - survival
          - creative
      hold_response: false  # Optional: Hold response until rule completes or an action signals early release (default: false)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `virtual_host_list` | - | Filter which virtual hosts can trigger this. See [Virtual host filtering](#virtual-host-filtering) for details. |
| `server_list` | - | Filter by server names. Maps server names to their `virtual_host` values from the `servers:` configuration. See [Server filtering](#server-filtering) for details. |
| `hold_response` | `false` | If `true`, holds the ping response until the rule execution completes or an action ([`allow_ping`](/actions/ping-management/allow-ping.md), [`deny_ping`](/actions/ping-management/deny-ping.md), [`respond_ping`](/actions/ping-management/respond-ping.md)) signals early release. |

### Virtual host filtering

Filter which virtual hosts can trigger this rule.

```{ .yaml }
virtual_host_list:
  mode: whitelist
  virtual_hosts:
    - example.com
    - mc.example.com
    - 192.168.1.100
```

- **`mode`**: `whitelist` (only listed hosts) or `blacklist` (all except listed hosts)
- **`virtual_hosts`**: List of virtual host names or addresses

### Server filtering

Filter which servers can trigger this rule by server name. The trigger will match pings based on the `virtual_host` configured for each server in the `servers:` section of your configuration.

```{ .yaml }
server_list:
  mode: whitelist
  servers:
    - survival
    - creative
```

- **`mode`**: `whitelist` (only listed servers) or `blacklist` (all except listed servers)
- **`servers`**: List of server names (as defined in the `servers:` section of your configuration)

!!! note "Server to virtual host mapping"
    When using `server_list`, the trigger automatically maps server names to their `virtual_host` values from your server configuration. Only servers with a configured `virtual_host` will be matched. Check [servers](/configuration/servers.md/#virtual-host) for more information.

!!! warning "Conflicting filter modes"
    When both `virtual_host_list` and `server_list` are specified with different modes (e.g., one is `whitelist` and the other is `blacklist`), the trigger will log a warning and default to `whitelist` mode. To avoid confusion, use the same mode for both filters, or use only one filter type.

## Context variables

| Variable | Description |
|----------|-------------|
| `ping` | ProxyPingEvent object |
| `ping.server.motd` | Server MOTD (in MiniMessage format) |
| `ping.server.player_count` | Current player count |
| `ping.server.max_players` | Maximum players |
| `ping.server.protocol_version` | Server protocol version |
| `ping.server.version_name` | Server version name |
| `ping.player.remote_address` | Client remote address |
| `ping.player.virtual_host` | Client virtual host |
| `ping.player.protocol_version` | Client protocol version |

## Examples

### Show cached MOTD for offline server
```{ .yaml }
rules:
  cached_motd:
    triggers:
      - ping:
          hold_response: true  # Hold response until respond_ping action is executed
          virtual_host_list:
            mode: whitelist
            virtual_hosts:
              - survival.example.com # Only show cached MOTD for survival.example.com
    conditions:
      mode: all
      checks:
        - server_status:
            server: survival
            status: offline  # Check if survival server is offline
    action:
      - respond_ping:
          ping: "${ping}"  # Ping event object (default: ${ping})
          use_cached_motd: true  # Show cached MOTD
          player_count: 0  # Show 0 players
          max_players: 0  # Show 0 max players
          protocol_version: -1  # Use -1 protocol version to show version name text
          version_name: "<blue>◉ Sleeping"  # Show version name text as sleeping
```

### Deny ping

```{ .yaml }
rules:
  deny_maintenance_ping:
    triggers:
      - ping:
          hold_response: true  # Hold response to use deny_ping action later
          virtual_host_list:
            mode: whitelist
            virtual_hosts:
              - survival.example.com  # Only deny ping for survival.example.com
    action:
      - deny_ping:  # Deny the ping request
          ping: "${ping}"  # Ping event object (default: ${ping})
```

### Dynamic MOTD with variables

```{ .yaml }
rules:
  dynamic_motd:
    triggers:
      - ping:
          hold_response: true  # Hold response to customize MOTD
    action:
      - respond_ping:
          motd: "<yellow>Players: ${ping.server.player_count}/${ping.server.max_players}</yellow>"  # Use variables in MOTD
```