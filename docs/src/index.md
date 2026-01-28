# AutoStartStop

AutoStartStop is a **Velocity** plugin for **automated server management** using a flexible, rule-based system.
You define **rules** that react to **triggers** (events like proxy start, player connect, cron schedules, etc.) and execute **actions** (start/stop servers, connect/disconnect players, log messages, and more), optionally gated by **conditions**.

## How it works

AutoStartStop evaluates your configuration as:

- **Settings**: Global plugin settings (timeouts, intervals, etc.).
- **Servers**: Which backend servers are managed, and how to control them (Shell, AMP, or Pterodactyl API).
- **Rules**: What should happen under which circumstances.
    - **Triggers**: When should a rule fire? (e.g. `connection`, `proxy_start`, `cron`, `empty_server`)
    - **Conditions** *(optional)*: Should it run right now? (e.g. “server is offline”, “player count > 0”)
    - **Actions**: What should happen? (e.g. `start`, `stop`, `send_message`, `disconnect`)

Here’s the overall `config.yml` shape at a glance:

```{ .yaml .no-copy }
settings: { ... }  # Global plugin settings (timeouts, intervals, etc.).
defaults: { ... }  # Global defaults for server configurations.
servers: { ... }   # Which backend servers are managed, and how to control them.
rules: { ... }     # What should happen under which circumstances.
```

## Key features

- **Rule-based automation**: Define rules that react to triggers and execute actions
- **Multiple control APIs**: Support for Shell commands, AMP API, and Pterodactyl Panel API
- **Flexible triggers**: Proxy lifecycle, player connections, cron schedules, empty server detection, ping/MOTD requests
- **Built-in templates**: Pre-configured templates for common automation patterns

## Example use cases

- **Auto-start on demand**: Start a server when a player tries to connect and it’s offline.
- **Auto-stop when idle**: Stop game servers after they’ve been empty for a duration.
- **Scheduled tasks**: Nightly restart, backups, or maintenance actions via cron rules.
- **Smarter server list**: Adjust ping/MOTD behavior while a server is online/offline.
- **Graceful shutdown**: On proxy shutdown, stop selected servers with warning messages to players.
- **Scheduled rewards**: Send hourly rewards or messages to players using cron triggers.

## Statistics

This plugin uses [bStats](https://bstats.org/docs/server-owners) to collect anonymous usage statistics. You can opt out by editing `plugins/bStats/config.txt`.