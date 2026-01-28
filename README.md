<p align="center">
  <a href="https://beyenilmez.github.io/autostartstop/">
    <img src="docs/src/assets/logo.png" width="160" alt="AutoStartStop Logo">
  </a>
</p>

<h1 align="center">AutoStartStop</h1>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0">
    <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3">
  </a>
  <a href="https://github.com/beyenilmez/autostartstop/releases">
    <img src="https://img.shields.io/github/v/release/beyenilmez/autostartstop?logo=github" alt="GitHub Release">
  </a>
  <a href="https://modrinth.com/plugin/autostartstop">
    <img src="https://img.shields.io/modrinth/v/autostartstop?logo=modrinth&label=Modrinth" alt="Modrinth">
  </a>
  <a href="https://hangar.papermc.io/beyenilmez/AutoStartStop">
    <img src="https://img.shields.io/badge/dynamic/json?color=FFA500&label=Hangar&query=version&url=https://hangar.papermc.io/api/v1/projects/beyenilmez/AutoStartStop&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAYAAACNiR0NAAAACXBIWXMAAATrAAAE6wHYKlsNAAAAGXRFWHRTb2Z0d2FyZQB3d3cuaW5rc2NhcGUub3Jnm+48GgAAA6hJREFUOI2FVG1MW2UYPe+9t71tmXRA5l2/pt1kbl3FmLFsaSXxAz+IQ81+4DSIjSYQN6MxzswgKmpC+LMQjVGbGTQuccagxi6LsX/kKw0VayVth64DypoFFYJj3vYWeu99/VHa9bqCz7/7nOec9zzve3MIpRSbVxsrpa++92moPjCUPr7/wUOej7fVrpDnXHS50jSzuRgh4szyqbPD2285f7HxVZvp7Xt1+lx4bWrrC8pjR4+gq0t3A2Mzh5emDr/52U+3NUjXFtmH5YRp92wdLyzVOPQK51QplfOy0mQ8PxSuKOj1endxHBckhLAFiBLCMDoCFaxKSgRKARW0OKGolE6NjIwcLuIcAODRth1P66uah1h1JyHXyQDA8zwsFgtSqVTBAQC2DM/n85r5giDDPnkHzzcPyZIGtFgs6Ovrg91uRzweRyAQwNjYGGRZ3vCamI/ipOF0e9CpJyxXDphMJvT398NutwMA3G43uru70dHRsaEYADCXZu9ks7vEg5Iup3md9vZ2WK1WzbDf78fg4KCmx7JEd3Wu+ZWS4C/JL19MxO5fmbMs/lNs6vV6tLS0aIjj4+PIZDIwGAyaPgUhn4fvaspdeehdAGBf79SZfr78uKt2+3fK7MXaPYQQuFwutLa2lkiKoiAWi8Hn80EQBEiShIWFhQKoKhllH5+ulhin1fjJbuapgxeafa7vVxPZ/aWVbTabxsX09DQ8Hg9GR0fhdrvR29sLjlt/TxVopGP6gcgzzMIVEmRMdnPnA+7UZQt7s3r9XliN4Pz8PKqrqyGKIgRBgMFgQE1NzfrKQMPMVtTWfSN1hj6oZ4CvlGpn08sHhN9+LQosLS1pBGVZRjQaRSgUQjQaBaUUuVyuhDtTgvnAobPWv6dXzqz/Km+pA6e93xqNxncAIJFIQJbl0lqEECSTSYTDYYTDYTgcDoiiWHJYl9lSn11e+/rZlxr5iuEgiiJ6enowMTEBSinMZjOy2WwJT6fTKM8AVcWWkwNt7x9zUZGrJAgAkUgEkUgEgiDA4/EgGAxWnKOAsqYox3Qm+hegDQc3z/MxhvmfRPtPybI8Pzw8fGvxu+SQUjqTz+cbu41Vj+zhjF49Q9g/TRkptfOP1eTton5y0Zd/YkecbLtpOfRGf+7HMsFs+QE35uGRo3sVlezzHz/Xdq2Kd09Ods3xcoPuNe8Xq/W23O9GR/DEZo43DFj/BeI6F/gwl+Hue/7U3SdH99py9xgdP5woJOLG9S+2F3zuid6QbAAAAABJRU5ErkJggg==" alt="Hangar">
  </a>
</p>

## Introduction

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

```yaml
settings: { ... }  # Global plugin settings (timeouts, intervals, etc.).
defaults: { ... }  # Global defaults for server configurations.
servers: { ... }   # Which backend servers are managed, and how to control them.
rules: { ... }     # What should happen under which circumstances.
```

## Key Features

- **Rule-based automation**: Define rules that react to triggers and execute actions
- **Multiple control APIs**: Support for Shell commands, AMP API, and Pterodactyl Panel API
- **Flexible triggers**: Proxy lifecycle, player connections, cron schedules, empty server detection, ping/MOTD requests
- **Built-in templates**: Pre-configured templates for common automation patterns

## Example Use Cases

- **Auto-start on demand**: Start a server when a player tries to connect and it’s offline.
- **Auto-stop when idle**: Stop game servers after they’ve been empty for a duration.
- **Scheduled tasks**: Nightly restart, backups, or maintenance actions via cron rules.
- **Smarter server list**: Adjust ping/MOTD behavior while a server is online/offline.
- **Graceful shutdown**: On proxy shutdown, stop selected servers with warning messages to players.
- **Scheduled rewards**: Send hourly rewards or messages to players using cron triggers.

## Requirements

- **Velocity 3.4.0**
- **Java 21** or higher

## Installation

1. Download the AutoStartStop JAR from one of the available platforms:
   - [GitHub Releases](https://github.com/beyenilmez/autostartstop/releases)
   - [Modrinth](https://modrinth.com/plugin/autostartstop)
   - [Hangar](https://hangar.papermc.io/beyenilmez/AutoStartStop)

2. Place the JAR file in your Velocity server's `plugins/` folder

3. Restart Velocity (or start it if not running)

4. The plugin will create a default configuration file at `plugins/autostartstop/config.yml`

## Documentation

Full documentation is available at: **https://beyenilmez.github.io/autostartstop/**

## Statistics

This plugin uses [bStats](https://bstats.org/docs/server-owners) to collect anonymous usage statistics. You can opt out by editing `plugins/bStats/config.txt`.

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](https://www.gnu.org/licenses/gpl-3.0.html) for more information.






