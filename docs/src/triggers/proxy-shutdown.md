# proxy_shutdown Trigger

Fires when the Velocity proxy shuts down.

## Configuration

This trigger has no configuration options.

```{ .yaml }
triggers:
  - proxy_shutdown:
```

## Context variables

This trigger does not emit any context variables.

## Example

```{ .yaml }
rules:
  stop_lobby_on_shutdown:
    triggers:
      - proxy_shutdown:
    action:
      - stop:
          server: lobby
```
