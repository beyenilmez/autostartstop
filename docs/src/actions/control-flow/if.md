# if Action

Executes actions conditionally based on condition checks. Supports `if`, `else_if`, and `else` branches.

## Configuration example

```{ .yaml }
action:
  - if:
      mode: all  # Condition evaluation mode (default: all)
      checks:  # Condition checks
        - string_equals:
            value: ${_trigger_type}
            equals: manual
      then:  # Actions if conditions are true
        - log:
            message: "Manual trigger executed"
      else_if:  # Additional condition branches (can have multiple)
        - mode: all
          checks:
            - string_equals:
                value: ${_trigger_type}
                equals: cron
          then:
            - log:
                message: "Cron trigger executed"
        - mode: all
          checks:
            - string_equals:
                value: ${_trigger_type}
                equals: connection
          then:
            - log:
                message: "Connection trigger executed"
        - mode: all
          checks:
            - string_equals:
                value: ${_trigger_type}
                equals: ping
          then:
            - log:
                message: "Ping trigger executed"
      else:  # Actions if no conditions match
        - log:
            message: "Other trigger type executed"
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `mode` | `all` | Condition evaluation mode: `all` (AND) or `any` (OR) |
| `checks` | - | List of condition checks |
| `then` | - | Actions to execute if conditions are true |
| `else_if` | - | List of additional condition branches with `mode`, `checks`, and `then` |
| `else` | - | Actions to execute if no conditions match |

!!! note "Condition checks"
    The `checks` and `mode` fields work exactly the same as in [rule conditions](/conditions/index.md). You can use any available condition type (e.g., `server_status`, `player_count`, `string_equals`, `number_compare`) and the same evaluation modes (`all` or `any`).