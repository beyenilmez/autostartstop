# allow_ping Action

Allows a ping request to proceed normally. Use this action when a ping trigger has `hold_response: true` and you want to allow the ping request to proceed. See [ping](/triggers/ping.md) for more information.

## Configuration

```{ .yaml }
action:
  - allow_ping:
      ping: ${ping}  # Ping event (default: ${ping})
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `ping` | `${ping}` | The ping event object |

## Example

```{ .yaml }
rules:
  allow_ping:
    triggers:
      - ping:
          hold_response: true  # Hold the ping response
    action:
      - allow_ping:  # Allow the ping request to proceed
          ping: ${ping}  # Ping event
      - log:
          message: "Ping request replied for ${ping.player.remote_address}"
```
This action will allow the ping request to proceed normally for the specified ping event, and log a message to the console.