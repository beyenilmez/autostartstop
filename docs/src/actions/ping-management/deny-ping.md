# deny_ping Action

Denies a ping request, preventing the ping response from being sent. Use this action when a ping trigger has `hold_response: true` and you want to deny the ping request. See [ping](/triggers/ping.md) for more information.

## Configuration

```{ .yaml }
action:
  - deny_ping:
      ping: ${ping}  # Ping event (default: ${ping})
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `ping` | `${ping}` | The ping event object |

## Example

```{ .yaml }
rules:
  deny_ping:
    triggers:
      - ping:
          hold_response: true  # Hold the ping response
          virtual_host_list:  # Only trigger for ping requests from the specified virtual hosts
            mode: whitelist
            virtual_hosts:
              - mc.example.com
              - 192.168.1.100
    action:
      - deny_ping:  # Deny the ping request
          ping: ${ping}  # Ping event
```
This rule will deny ping requests for the specified virtual hosts.