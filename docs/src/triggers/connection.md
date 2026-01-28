# connection Trigger

Fires when a player attempts to connect to a server.

## Configuration

```{ .yaml }
triggers:
  - connection:
      server_list:            # Optional: Filter by server list
        mode: whitelist       # 'whitelist' or 'blacklist'
        servers:              # List of server names (as defined in velocity.toml)
          - survival
          - creative
      player_list:            # Optional: Filter by player list
        mode: whitelist       # 'whitelist' or 'blacklist'
        players:              # List of player names
          - Admin1
          - Admin2
      deny_connection: false  # Optional: Deny connection (default: false)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server_list` | - | Filter which servers can trigger this. See [Server list filtering](#server-list-filtering) for details. |
| `player_list` | - | Filter which players can trigger this. See [Player list filtering](#player-list-filtering) for details. |
| `deny_connection` | `false` | If `true`, the connection is denied. Use [`allow_connection`](/actions/player-management/allow-connection.md) action to allow it. |

### Server list filtering

Filter which servers can trigger this rule.

```{ .yaml }
server_list:
  mode: whitelist
  servers:
    - survival
    - creative
```

- **`mode`**: `whitelist` (only listed servers) or `blacklist` (all except listed servers)
- **`servers`**: List of server names (as defined in `velocity.toml`)

### Player list filtering

Filter which players can trigger this rule.

```{ .yaml }
player_list:
  mode: whitelist
  players:
    - Admin1
    - Admin2
```

- **`mode`**: `whitelist` (only listed players) or `blacklist` (all except listed players)
- **`players`**: List of player names

## Context variables

| Variable | Description |
|----------|-------------|
| `connection` | Connection event |
| `connection.player` | Player object |
| `connection.player.name` | Username |
| `connection.player.uuid` | Player UUID |
| `connection.server` | RegisteredServer object |
| `connection.server.name` | Server name |
| `connection.server.status` | Server status (`online`/`offline`) |
| `connection.server.players` | Collection of players on server |
| `connection.server.player_count` | Number of players on server |

## Example

```{ .yaml }
rules:
  auto_start_on_connect:
    triggers:
      - connection:
          server_list:
            mode: whitelist
            servers:  # Only trigger for connections to these servers
              - survival
              - creative
          player_list:
            mode: whitelist
            players:  # Only these players can trigger auto-start
              - Admin1
              - Admin2
          deny_connection: true  # Deny connection, allow it later if needed
    conditions:
      mode: all
      checks:
        - server_status:
            server: ${connection.server.name}
            status: offline  # Only start if server is offline
    action:
      - start:
          server: ${connection.server.name}  # Start the server
      - sleep:
          duration: 20s  # Wait for server to start
      - allow_connection:  # Allow the player to connect after server is ready
          connection: ${connection}  # Connection event (default: ${connection})
          server: ${connection.server}  # Server to allow connection to (default: ${connection.server})
```
