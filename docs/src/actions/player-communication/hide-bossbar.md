# hide_bossbar Action

Hides a bossbar from one or more players using the bossbar ID that was used when showing it.

## Configuration

```{ .yaml }
action:
  - hide_bossbar:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
      servers:            # All players on servers
        - survival
        - creative
      id: "maintenance"   # Bossbar ID to hide
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | All players on a single server |
| `servers` | - | All players on multiple servers |
| `id` | - | Bossbar ID to hide (must match the ID used in `show_bossbar`) |

!!! note "Player targeting"
    At least one of `player`, `players`, `server`, or `servers` must be provided.

## Example

```{ .yaml }
rules:
  hide_bossbar:
    triggers:
      - manual:
          id: 'hidebossbar'  # Trigger with: /autostartstop trigger hidebossbar <server_name> <id>
    action:
      - hide_bossbar:
          server: ${manual.args.0}  # Server name from command argument
          id: ${manual.args.1}  # Bossbar ID to hide
```
This action will hide the bossbar with the specified ID from all players on the specified server.