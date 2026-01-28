# show_bossbar Action

Shows a bossbar to one or more players. The bossbar can be hidden later using the `hide_bossbar` action with the same ID. See [hide_bossbar](hide-bossbar.md) for more information.

## Configuration

```{ .yaml }
action:
  - show_bossbar:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
      servers:            # All players on servers
        - survival
        - creative
      id: "maintenance"                              # Unique bossbar ID
      message: "<red>Maintenance in progress</red>"  # Message (MiniMessage format)
      color: WHITE                                   # Color (default: WHITE)
      overlay: PROGRESS                              # Overlay (default: PROGRESS)
      progress: 1.0                                  # Progress (0.0-1.0, default: 1.0)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | All players on a single server |
| `servers` | - | All players on multiple servers |
| `id` | - | Unique bossbar ID for later reference |
| `message` | - | Bossbar message in MiniMessage format |
| `color` | `WHITE` | Bossbar color. Valid values: `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE` |
| `overlay` | `PROGRESS` | Overlay style. Valid values: `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20` |
| `progress` | `1.0` | Progress value between 0.0 and 1.0 |

!!! note "Player targeting"
    At least one of `player`, `players`, `server`, or `servers` must be provided.

## Example

```{ .yaml }
rules:
  show_bossbar:
    triggers:
      - manual:
          id: 'bossbar'  # Trigger with: /autostartstop trigger bossbar <server_name> <id> <message>
    action:
      - show_bossbar:
          server: ${manual.args.0}  # Server name from command argument
          id: ${manual.args.1}  # Bossbar ID from command argument
          message: ${manual.args.2}  # Message from command argument
          color: RED  # Bossbar color
          progress: 0.5  # Progress value (0.0-1.0)
```
This action will show a bossbar with the specified ID and message to all players on the specified server.