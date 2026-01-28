# Installation

## Requirements

- **Velocity 3.4.0**
- **Java 21** or higher

## Steps

### 1. Download

Download the AutoStartStop JAR from one of the available platforms:

- [GitHub Releases](https://github.com/beyenilmez/autostartstop/releases)
- [Modrinth](https://modrinth.com/plugin/autostartstop)
- [Hangar](https://hangar.papermc.io/beyenilmez/AutoStartStop)

### 2. Install

To install AutoStartStop, place the JAR file in your Velocity server’s `plugins/` folder and restart Velocity if it is already running:

```{ .text .no-copy }
velocity/
└── plugins/
    └── AutoStartStop-<version>.jar
```

On first run, the plugin will create its default configuration file:

```{ .text .no-copy }
velocity/
└── plugins/
    └── autostartstop/
        └── config.yml
```

### 3. Verify

You can verify the installation by checking the console for the following message:

```{ .text .no-copy }
[AutoStartStop]: AutoStartStop enabled successfully
```

!!! tip "No success message?"
    If you don't see the message, confirm the plugin JAR is in `plugins/`, restart Velocity, and check the console for any AutoStartStop-related errors during startup.

## What's next

- [Basic Concepts](basic-concepts.md) - Learn how rules, triggers, conditions, and actions work
- [Configuration](/configuration/index.md) - Configure servers and rules
- [Rule Templates](/rule-templates/index.md) - Use pre-built templates for common patterns