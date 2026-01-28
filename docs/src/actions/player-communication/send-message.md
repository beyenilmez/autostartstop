# send_message Action

Sends a message to one or more players.

## Configuration

```{ .yaml }
action:
  - send_message:
      player: PlayerName  # Single player
      players:            # Multiple players
        - Player1
        - Player2
      server: survival    # All players on server
       servers:           # All players on servers
        - survival
        - creative
      message: "<yellow>Hello!</yellow>"  # Message (MiniMessage format)
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
  send_message:
    triggers:
      - manual:
          id: 'message'  # Trigger with: /autostartstop trigger message <server_name> <message>
    action:
      - send_message:
          server: ${manual.args.0}  # Server name from command argument
          message: ${manual.args.1}  # Message from command argument
```
This action will send the specified message to all players on the specified server.