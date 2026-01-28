# sleep Action

Waits for a specified duration before continuing to the next action.

## Configuration

```{ .yaml }
action:
  - sleep:
      duration: 30s  # Duration (e.g., "30s", "1m", "2h")
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `duration` | - | The duration to wait. Supports formats like `30s`, `1m`, `2h`, `10t` |

!!! tip "Duration format"
    The `duration` field supports multiple formats:
    
    - **Ticks**: `10t`, `20t` (1 tick = 50ms, 20 ticks per second)
    - **Milliseconds**: `500ms`, `1000ms`
    - **Seconds**: `1s`, `3s`, `5.5s`
    - **Minutes**: `1m`, `2m`
    - **Hours**: `1h`, `2h`

## Example

```{ .yaml }
rules:
  sleep:
    triggers:
      - manual:
          id: 'wait'  # Trigger with: /autostartstop trigger wait
    action:
      - log:
          message: "Waiting 30 seconds..."  # Log before waiting
      - sleep:
          duration: 30s  # Wait for 30 seconds
      - log:
          message: "Wait complete!"  # Log after waiting
```
This rule will wait for 30 seconds between two log messages.