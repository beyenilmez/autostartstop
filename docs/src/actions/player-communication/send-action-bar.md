# send_action_bar Action

Sends an action bar message to one or more players.

## Configuration

```{ .yaml }
action:
  - send_action_bar:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
      servers:            # All players on servers
        - survival
        - creative
      message: "<yellow>Action bar message</yellow>"  # Message (MiniMessage format)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `player` | - | Single player name or UUID |
| `players` | - | List of player names or UUIDs |
| `server` | - | All players on a single server |
| `servers` | - | All players on multiple servers |
| `message` | - | Message to send in MiniMessage format |

!!! note "Player targeting"
    At least one of `player`, `players`, `server`, or `servers` must be provided.

## Example

```{ .yaml }
rules:
  send_action_bar:
    triggers:
      - manual:
          id: 'actionbar'  # Trigger with: /autostartstop trigger actionbar <player_name> <message>
    action:
      - send_action_bar:
          player: ${manual.args.0}  # Player name from command argument
          message: ${manual.args.1}  # Message from command argument
```
This action will send the specified action bar message to the specified player.