# clear_title Action

Clears the title for one or more players.

## Configuration

```{ .yaml }
action:
  - clear_title:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
       servers:           # All players on servers
        - survival
        - creative
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | All players on a single server |
| `servers` | - | All players on multiple servers |

!!! note "Player targeting"
    At least one of `player`, `players`, `server`, or `servers` must be provided.

## Example

```{ .yaml }
rules:
  clear_title:
    triggers:
      - manual:
          id: 'cleartitle'  # Trigger with: /autostartstop trigger cleartitle <server_name>
    action:
      - clear_title:
          server: ${manual.args.0}  # Server name from command argument
```
This action will clear the title for all players on the specified server.
