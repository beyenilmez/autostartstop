# manual Trigger

Fires via `/autostartstop trigger <id> [args...]` command.

## Configuration

```{ .yaml }
triggers:
  - manual:
      id: 'start_server'  # Required: Trigger ID
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `id` | - | Unique identifier for this manual trigger (required) |

## Context variables

| Variable | Description |
|----------|-------------|
| `manual.id` | The trigger ID |
| `manual.args.0`, `manual.args.1`, ... | Command arguments (indexed from 0) |
| `manual.args.length` | Number of arguments |

## Usage

To fire a manual trigger, use the command:

```
/autostartstop trigger <id> [args...]
```

Or use the alias:

```
/ass trigger <id> [args...]
```

## Examples

### Connect player to server

```{ .yaml }
rules:
  manual_connect:
    triggers:
      - manual:
          id: 'connect'  # Trigger ID: /ass trigger connect <player_name> <server_name>
    action:
      - log:
          message: "Connecting ${manual.args.0} to ${manual.args.1}"  # Log the connection
      - connect:
          player: ${manual.args.0}  # First argument: player name
          server: ${manual.args.1}  # Second argument: server name
```

Usage: `/ass trigger connect PlayerName survival`

### Disconnect player

```{ .yaml }
rules:
  manual_kick:
    triggers:
      - manual:
          id: 'kick'  # Trigger ID: /ass trigger kick <player_name> [reason]
    action:
      - log:
          message: "Kicking player ${manual.args.0} (reason: ${manual.args.1})"  # Log the kick
      - disconnect:
          player: ${manual.args.0}
          reason: "<red>You have been kicked by an administrator.</red><br><yellow>Reason: ${manual.args.1}</yellow>"  # Kick with reason
```

Usage: `/ass trigger kick PlayerName "Reason for kick"`
