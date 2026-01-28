# number_compare Condition

Compares a numeric value against min, max, or equals constraints.

## Configuration

```{ .yaml }
conditions:
  mode: all
  checks:
    - number_compare:
        value: ${ping.server.player_count}  # Value to compare (required)
        min: 0                              # Optional: Minimum value
        max: 100                            # Optional: Maximum value
        equals: 50                          # Optional: Exact value
        invert: false                       # Optional: Invert the result (default: false)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `value` | - | Numeric value to compare (required) |
| `min` | - | Minimum value (inclusive) |
| `max` | - | Maximum value (inclusive) |
| `equals` | - | Exact value |
| `invert` | `false` | If `true`, negates the condition result |

!!! note "Constraint evaluation"
    The condition evaluates to `true` if the value satisfies all specified constraints:

    - If `equals` is specified, value must equal this number
    - If `min` is specified, value must be >= `min`
    - If `max` is specified, value must be <= `max`
    
    You can use `min` and `max` together to check if a value is within a range.

## Examples

### Check exact value

```{ .yaml }
rules:
  exact_player_count:
    triggers:
      - cron:
          expression: '*/5 * * * *'  # Every 5 minutes
    conditions:
      mode: all
      checks:
        - number_compare:
            value: ${surival.player_count}
            equals: 0  # Exactly 0 players
    action:
      - log:
          message: "Survival server is empty"
```

### Check value range

```{ .yaml }
rules:
  moderate_load:
    triggers:
      - cron:
          expression: '*/10 * * * *'  # Every 10 minutes
    conditions:
      mode: all
      checks:
        - number_compare:
            value: ${survival.player_count}
            min: 5   # At least 5
            max: 20  # At most 20
    action:
      - log:
          message: "Survival server has moderate load: ${survival.player_count} players"
```

### Check minimum value

```{ .yaml }
rules:
  high_load:
    triggers:
      - cron:
          expression: '*/15 * * * *'  # Every 15 minutes
    conditions:
      mode: all
      checks:
        - number_compare:
            value: ${survival.player_count}
            min: 50  # At least 50 players
    action:
      - log:
          message: "Survival server is under high load: ${survival.player_count} players"
```
