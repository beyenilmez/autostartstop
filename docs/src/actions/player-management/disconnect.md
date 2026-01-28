# disconnect Action

Disconnects one or more players from the proxy.

## Configuration

```{ .yaml }
action:
  - disconnect:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
      servers:            # All players on servers
        - survival
        - creative
      reason: "<red>Disconnected</red>"  # Optional: Disconnect reason (MiniMessage format)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | All players on a single server |
| `servers` | - | All players on multiple servers |
| `reason` | - | Disconnect reason in MiniMessage format |

!!! note "Player targeting"
    At least one of `player`, `players`, `server`, or `servers` must be provided.

## Example

```{ .yaml }
rules:
  disconnect_server:
    triggers:
      - manual:
          id: 'disconnect'  # Trigger with: /autostartstop trigger disconnect <server_name> <reason>
    action:
      - disconnect:
          server: ${manual.args.0}  # Server name from command argument
          reason: ${manual.args.1}  # Disconnect reason from command argument (optional)
```
This rule allows you to manually disconnect all players from a specified server with a custom reason.