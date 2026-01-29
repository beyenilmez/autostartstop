# connect Action

Connects one or more players to the specified server.

## Configuration

```{ .yaml }
action:
  - connect:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # Target server
      error_message: '<red>Could not connect to the server.</red>'  # Optional; sent to player on connection failure
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | Target server name |
| `error_message` | `<red>Could not connect to ${connect_server}: ${connect_error_reason}</red>` | MiniMessage sent to the player when the connection fails. Supports variables: `connect_server` (target server name), `connect_error_reason` (failure reason from the server or exception message). |

## Example

```{ .yaml }
rules:
  transfer_players:
    triggers:
      - manual:
          id: 'transfer'  # Trigger with: /autostartstop trigger transfer <source_server> <target_server>
    action:
      - connect:
          server: ${manual.args.1}  # Target server (second argument)
          players: ${${manual.args.0}.players}  # All players from source server (first argument)
```
This will transfer all players from the source server to the target server when the command is executed.