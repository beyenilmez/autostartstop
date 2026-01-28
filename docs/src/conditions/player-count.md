# player_count Condition

Checks player count on a server against min, max, or equals constraints.

## Configuration

```{ .yaml }
conditions:
  mode: all
  checks:
    - player_count:
        server: survival  # Server name (required)
        min: 0            # Optional: Minimum player count
        max: 10           # Optional: Maximum player count
        equals: 5         # Optional: Exact player count
        invert: false     # Optional: Invert the result (default: false)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | The server name to check (required) |
| `min` | - | Minimum player count (inclusive) |
| `max` | - | Maximum player count (inclusive) |
| `equals` | - | Exact player count |
| `invert` | `false` | If `true`, negates the condition result |

!!! note "Constraint evaluation"
    The condition evaluates to `true` if the player count satisfies all specified constraints:
    
    - If `equals` is specified, player count must equal this value
    - If `min` is specified, player count must be >= `min`
    - If `max` is specified, player count must be <= `max`
    
    You can use `min` and `max` together to check if player count is within a range.

## Examples

### Check if server is empty

```{ .yaml }
rules:
  stop_empty_server:
    triggers:
      - cron:
          expression: '0 * * * *'  # Every hour
    conditions:
      mode: all
      checks:
        - player_count:
            server: survival
            equals: 0  # Server has no players
    action:
      - stop:
          server: survival
```

### Check if server has players

```{ .yaml }
rules:
  notify_when_busy:
    triggers:
      - cron:
          expression: '*/5 * * * *'  # Every 5 minutes
    conditions:
      mode: all
      checks:
        - player_count:
            server: survival
            min: 10  # At least 10 players
    action:
      - log:
          message: "Survival server is busy with ${survival.player_count} players"
```

### Check player count range

```{ .yaml }
rules:
  moderate_load:
    triggers:
      - cron:
          expression: '*/10 * * * *'  # Every 10 minutes
    conditions:
      mode: all
      checks:
        - player_count:
            server: survival
            min: 5   # At least 5 players
            max: 20  # At most 20 players
    action:
      - log:
          message: "Survival server has moderate load: ${survival.player_count} players"
```
