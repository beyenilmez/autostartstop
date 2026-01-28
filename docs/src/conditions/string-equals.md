# string_equals Condition

Compares two string values for equality.

## Configuration

```{ .yaml }
conditions:
  mode: all
  checks:
    - string_equals:
        value: ${connection.player.name}  # First value (required)
        equals: Admin1                    # Second value to compare (required)
        ignore_case: false                # Optional: Case-insensitive comparison (default: false)
        invert: false                     # Optional: Invert the result (default: false)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `value` | - | First string value to compare (required) |
| `equals` | - | Second string value to compare (required) |
| `ignore_case` | `false` | If `true`, performs case-insensitive comparison |
| `invert` | `false` | If `true`, negates the condition result |

## Examples

### Case-insensitive comparison

```{ .yaml }
rules:
  case_insensitive_check:
    triggers:
      - manual:
          id: 'check'
    conditions:
      mode: all
      checks:
        - string_equals:
            value: ${manual.args.0}
            equals: 'admin'
            ignore_case: true  # 'Admin', 'ADMIN', 'admin' all match
    action:
      - log:
          message: "Admin command executed"
```

### Check trigger type

```{ .yaml }
rules:
  test_rule:
    triggers:
      - manual:
          id: 'test'
      - cron:
          expression: '0 * * * *'
    action:
      - if:
          mode: all
          checks:
            - string_equals: # Compare the trigger type with the value
                value: ${_trigger_type}
                equals: manual
          then:
            - log:
                message: "Manual trigger executed"
          else:
            - log:
                message: "Cron trigger executed"
```
