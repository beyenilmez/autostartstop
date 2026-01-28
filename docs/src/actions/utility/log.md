# log Action

Logs a message to the console at a specified log level.

## Configuration

```{ .yaml }
action:
  - log:
      message: "Server started successfully"  # Message to log
      level: INFO                             # Log level (default: INFO)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `message` | - | The message to log |
| `level` | `INFO` | The log level. Valid values: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |

## Example

```{ .yaml }
rules:
  log:
    triggers:
      - manual:
          id: 'log'  # Trigger with: /autostartstop trigger log <message>
    action:
      - log:
          message: ${manual.args.0}  # Message from command argument
          level: INFO  # Log level
```
This rule will log the specified message to the console at INFO level.