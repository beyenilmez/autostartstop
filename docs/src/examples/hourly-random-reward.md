# Hourly Random Reward

This example demonstrates how to use AutoStartStop to automatically send commands to servers on a schedule. In this case, a random player receives a diamond every hour, creating an engaging reward system for your players.

## Configuration

```{ .yaml }
servers:
  survival:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'instance_management_bot'
      password: '123456789+Abc'
      instance_id: 'd8014792-89c8-4392-86f5-4023c3108f2c'

rules:
  hourly_diamond_reward:
    triggers:
      - cron:
          format: 'UNIX'  # Available formats: UNIX, QUARTZ, CRON4J, SPRING, SPRING53
          expression: '0 * * * *'  # Every hour at minute 0
          time_zone: 'UTC+00:00'
    action:
      - send_command:
          server: survival
          command: "give @r diamond 1"
```

!!! note "Cron Expressions"
    The cron expression `0 * * * *` means "at minute 0 of every hour" (i.e., every hour on the hour). You can adjust this to run at different intervals:
    
    - `0 * * * *` - Every hour at minute 0
    - `*/30 * * * *` - Every 30 minutes
    - `0 */2 * * *` - Every 2 hours at minute 0
    - `0 12,18 * * *` - At 12:00 PM and 6:00 PM daily
    
    You can use [crontab.guru](https://crontab.guru) to help create and understand UNIX cron expressions. The plugin supports multiple cron formats (UNIX, QUARTZ, CRON4J, SPRING, SPRING53) and time zones.

## What This Configuration Does

Every hour, a random player on the survival server receives a diamond

## Variation: Multiple Rewards

```{ .yaml }
hourly_rewards:
  triggers:
    - cron:
        expression: '0 * * * *'
  action:
    - send_command:
        server: survival
        command: "give @r diamond 1" # Give 1 diamond to a random player
    - send_command:
        server: survival
        command: "give @a[limit=2,sort=random] emerald 2" # Give 2 emeralds to 2 random players
    - send_command:
        server: survival
        command: "give @a[limit=4,sort=random] gold_ingot 5" # Give 5 gold ingots to 4 random players
```