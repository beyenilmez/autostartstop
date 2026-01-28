# Quick Start

Follow these steps to get AutoStartStop working in minutes. This guide will help you set up your first automation rule.

## Step 1: Install AutoStartStop

If you haven't installed AutoStartStop yet, see the [Installation](installation.md) guide.

After installation, the plugin will create a configuration file at `plugins/autostartstop/config.yml`.

## Step 2: Configure Your Server

Open `plugins/autostartstop/config.yml` and configure your server. You can find detailed information about server configuration in the [Servers](/configuration/servers.md) documentation.

## Step 3: Create Your First Rule

Add a rule to automate server management. For common patterns like "start server on connection", you can check out the [Rule Templates](/rule-templates/index.md) page.

If you want to understand how rules work, see [Basic Concepts](basic-concepts.md) for an explanation of triggers, conditions, and actions.

## Step 4: Reload Configuration

After making changes to `config.yml`, reload the configuration:

```
/ass reload
```

For more information about commands, see [Commands](/commands.md).

## Step 5: Test

Test your configuration to make sure everything works as expected.

## Next Steps

- [Commands](/commands.md) - Learn about available commands and permissions
- [Basic Concepts](basic-concepts.md) - Understand how rules, triggers, and actions work
- [Configuration](/configuration/index.md) - Learn about all configuration options
- [Rule Templates](/rule-templates/index.md) - Explore pre-built automation patterns
- [Examples](/examples/index.md) - Explore examples of how to use AutoStartStop
