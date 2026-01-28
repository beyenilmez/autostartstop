# proxy_start Trigger

Fires when the Velocity proxy starts.

## Configuration

This trigger has no configuration options.

```{ .yaml }
triggers:
  - proxy_start:
```

## Context variables

This trigger does not emit any context variables.

## Example

```{ .yaml }
rules:
  start_lobby_on_proxy_start:
    triggers:
      - proxy_start:
    action:
      - start:
          server: lobby
```
