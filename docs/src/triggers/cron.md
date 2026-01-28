# cron Trigger

Fires on a schedule defined by a cron expression.

## Configuration

```{ .yaml }
triggers:
  - cron:
      expression: '0 3 * * *'  # Required: Cron expression
      time_zone: 'UTC'         # Optional: Time zone (default: UTC)
      format: 'UNIX'           # Optional: Cron format (default: UNIX)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `expression` | - | The cron expression (required) |
| `time_zone` | `UTC` | The time zone for the schedule (e.g., `Europe/Istanbul`, `America/New_York`, `UTC`, `UTC+3`, `UTC-5:30:00`) |
| `format` | `UNIX` | The cron format. Valid values: `UNIX`, `QUARTZ`, `CRON4J`, `SPRING`, `SPRING53`. See [Cron formats](#cron-formats) for details. |

## Cron formats

| Format | Description |
|--------|-------------|
| `UNIX` | Standard UNIX cron format (default) |
| `QUARTZ` | Quartz scheduler format |
| `CRON4J` | Cron4j format |
| `SPRING` | Spring framework format |
| `SPRING53` | Spring 5.3+ format |

!!! tip "Cron expression helper"
    You can use [crontab.guru](https://crontab.guru) to help create and understand UNIX cron expressions.

## Context variables

| Variable | Description |
|----------|-------------|
| `cron.expression` | The cron expression |
| `cron.format` | The cron format used |
| `cron.time_zone` | The time zone used (ISO-8601) |
| `cron.scheduled_time` | The scheduled execution time (ISO-8601) |
| `cron.actual_time` | The actual execution time (ISO-8601) |

## Examples

### Daily restart

```{ .yaml }
rules:
  nightly_restart:
    triggers:
      - cron:
          expression: '0 3 * * *'  # Every day at 3 AM
          time_zone: 'Europe/Istanbul'  # Use Istanbul timezone
    action:
      - send_message:
          server: survival
          message: "<yellow>Server will restart in 1 minute.</yellow>"  # Notify players
      - sleep:
          duration: 1m  # Wait 1 minute
      - restart:
          server: survival  # Restart the server
      - log:
          message: "Nightly restart completed at ${cron.actual_time}"  # Log the restart
```

### Complex schedule

```{ .yaml }
rules:
  weekly_maintenance:
    triggers:
      - cron:
          expression: '0 2 * * 0'  # Every Sunday at 2 AM
          time_zone: 'UTC+3'  # Use UTC+3 offset
    action:
      - log:
          message: "Starting weekly maintenance at ${cron.actual_time}"  # Log start time
      - send_message:
          servers: [survival, creative]
          message: "<red>Weekly maintenance in progress. Some servers may be unavailable.</red>"  # Notify all players
      - exec:
          command: './maintenance.sh'  # Run maintenance script
          working_directory: '/opt/minecraft/scripts'
      - log:
          message: "Weekly maintenance completed"  # Log completion
```
