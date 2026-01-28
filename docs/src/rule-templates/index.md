# Templates

Templates are pre-built rule configurations that simplify common automation scenarios. Instead of manually defining triggers, conditions, and actions, templates provide ready-to-use solutions for frequently needed patterns.

## Available templates

- **[start_on_proxy_start](start-on-proxy-start.md)**: Starts servers when the proxy starts
- **[stop_on_proxy_shutdown](stop-on-proxy-shutdown.md)**: Stops servers when the proxy shuts down
- **[start_on_connection](start-on-connection.md)**: Starts a server when a player attempts to connect to it
- **[stop_on_empty](stop-on-empty.md)**: Automatically stops servers after they've been empty for a specified duration
- **[respond_ping](respond-ping.md)**: Customizes ping/MOTD responses based on server status

## Using templates

Templates are used in rules by specifying the `template` field instead of `triggers`, `conditions`, and `action`:

```{ .yaml }
rules:
  <rule_name>:
    enabled: true              # Enable/disable rule (default: true)
    template: <template_name>  # Template type
    # ... template-specific configuration
```

Each template has its own configuration options. See the individual template documentation pages for details.

## Template vs. manual rules

Templates are convenient shortcuts for common patterns, but you can always achieve the same functionality using manual triggers, conditions, and actions. Templates internally use the same triggers and actions that are available for manual configuration.