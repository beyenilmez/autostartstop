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
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | Target server name |

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