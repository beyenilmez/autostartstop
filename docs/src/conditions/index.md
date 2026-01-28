# Conditions

Conditions define **if** a rule should execute. They filter triggers to ensure actions only run under specific circumstances.

## Available conditions

- **[server_status](server-status.md)**: Check if server is online/offline
- **[player_count](player-count.md)**: Check player count on a server
- **[string_equals](string-equals.md)**: Compare two strings
- **[number_compare](number-compare.md)**: Compare numeric values

## Condition evaluation mode

Conditions support two evaluation modes:

- **`all`** (default): All conditions must be `true` for the rule to execute (AND logic)
- **`any`**: At least one condition must be `true` for the rule to execute (OR logic)

```{ .yaml }
conditions:
  mode: all  # All conditions must pass (default)
  checks:
    - server_status:
        server: survival
        status: online
    - player_count:
        server: survival
        min: 1
```

```{ .yaml }
conditions:
  mode: any  # At least one condition must pass
  checks:
    - server_status:
        server: survival
        status: online
    - server_status:
        server: creative
        status: online
```

## Inverting conditions

All conditions support the `invert` parameter to negate the result. Set `invert: true` to reverse the condition's logic.

```{ .yaml }
server_status:
  server: survival
  status: online
  invert: true  # Returns true if server is NOT online
```
