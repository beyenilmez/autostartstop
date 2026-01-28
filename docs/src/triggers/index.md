# Triggers

Triggers define **when** a rule should fire. A rule can have multiple triggers, and the rule fires when any trigger activates.

## Available triggers

- **[proxy_start](proxy-start.md)**: Fires when the proxy starts
- **[proxy_shutdown](proxy-shutdown.md)**: Fires when the proxy shuts down
- **[connection](connection.md)**: Fires when a player connects to a server
- **[empty_server](empty-server.md)**: Fires when a server has been empty for a duration
- **[cron](cron.md)**: Fires when a cron expression is matched
- **[ping](ping.md)**: Fires when a client pings the proxy (server list/MOTD requests)
- **[manual](manual.md)**: Fires via `/autostartstop trigger` command

## Common context variables

All triggers emit a common context variable:

| Variable | Description |
|----------|-------------|
| `_trigger_type` | The type of trigger that fired (e.g., `proxy_start`, `proxy_shutdown`, etc.) |

This variable can be used in actions and conditions to determine which trigger activated the rule, which is especially useful when a rule has multiple triggers.

Each trigger also emits trigger-specific context variables. See the individual trigger documentation pages for details.
