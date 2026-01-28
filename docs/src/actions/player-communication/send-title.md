# send_title Action

Sends a title and optional subtitle to one or more players. The title can be cleared later using the `clear_title` action. See [clear_title](clear-title.md) for more information.

## Configuration

```{ .yaml }
action:
  - send_title:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
       servers:           # All players on servers
        - survival
        - creative
      title: "<yellow>Welcome!</yellow>"        # Title (MiniMessage format)
      subtitle: "<gray>Enjoy your stay</gray>"  # Subtitle (MiniMessage format)
      fade_in: 500ms                            # Fade in duration (default: 500ms)
      stay: 3s                                  # Stay duration (default: 3s)
      fade_out: 500ms                           # Fade out duration (default: 500ms)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | All players on a single server |
| `servers` | - | All players on multiple servers |
| `title` | - | Title text in MiniMessage format |
| `subtitle` | - | Subtitle text in MiniMessage format |
| `fade_in` | `500ms` | Fade in duration (e.g., `500ms`, `1s`) |
| `stay` | `3s` | How long the title stays visible |
| `fade_out` | `500ms` | Fade out duration |

!!! tip "Duration format"
    Duration fields (`fade_in`, `stay`, `fade_out`) support multiple formats:
    
    - **Ticks**: `10t`, `20t` (1 tick = 50ms, 20 ticks per second)
    - **Milliseconds**: `500ms`, `1000ms`
    - **Seconds**: `1s`, `3s`, `5s`
    - **Minutes**: `1m`, `2m`
    - **Hours**: `1h`, `2h`

!!! note "Player targeting"
    At least one of `player`, `players`, `server`, or `servers` must be provided.

## Example

```{ .yaml }
rules:
  send_title:
    triggers:
      - manual:
          id: 'title'  # Trigger with: /autostartstop trigger title <player_name>
    action:
      - send_title:
          player: ${manual.args.0}  # Player name from command argument
          title: "<yellow><bold>Welcome!</bold></yellow>"  # Title message
          subtitle: "<gray>Enjoy your stay</gray>"  # Subtitle message
          stay: 5s  # How long the title stays visible
```
This action will send a title and subtitle to the specified player.
