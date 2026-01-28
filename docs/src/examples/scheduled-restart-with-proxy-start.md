# Scheduled Restart

This example demonstrates how to configure AutoStartStop to automatically start servers when the proxy starts, stop them gracefully when the proxy shuts down, and perform scheduled restarts using cron expressions. This is useful for maintaining server health, ensuring clean shutdowns, and keeping servers available when the proxy comes online.

## Configuration

```{ .yaml }
defaults:
  server:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'instance_management_bot'
      password: '123456789+Abc'

servers:
  survival:
    control_api:
      instance_id: 'd8014792-89c8-4392-86f5-4023c3108f2c'
  creative:
    control_api:
      instance_id: 'f648dfa1-e9c7-4291-80a5-7bc9da28c171'

rules:
  start_on_proxy_start:
    template: 'start_on_proxy_start'
    servers: [survival, creative]
    
  stop_on_proxy_shutdown:
    template: 'stop_on_proxy_shutdown'
    servers: [survival, creative]

  daily_restart:
    triggers:
      - cron:
          format: 'UNIX'  # Available formats: UNIX, QUARTZ, CRON4J, SPRING, SPRING53
          expression: '0 3 * * *'  # Every day at 3 AM
          time_zone: 'UTC+00:00'
    action:
      - send_message:
          servers: [survival, creative]
          message: "<yellow>Server will restart in 1 minute.</yellow>"
      - sleep:
          duration: 1m
      - restart:
          server: survival
          wait_for_completion: false # Run asynchronously
      - restart:
          server: creative
      - log:
          message: "Daily restart completed at ${cron.actual_time}"
```

!!! note "Cron Expressions"
    Cron expressions allow you to schedule tasks at specific times. The format used in this example is UNIX cron format:
    
    - `0 3 * * *` means "at 3:00 AM every day"
    - `0 4 * * 0` means "at 4:00 AM every Sunday"
    
    You can use [crontab.guru](https://crontab.guru) to help create and understand UNIX cron expressions. The plugin supports multiple cron formats (UNIX, QUARTZ, CRON4J, SPRING, SPRING53) and time zones.

## What This Configuration Does

This configuration demonstrates scheduled server management:

1. **Auto-start on proxy start**: Both servers start automatically when the proxy starts, ensuring they're ready for players immediately
2. **Auto-stop on proxy shutdown**: All servers stop cleanly when the proxy shuts down
3. **Daily restart**: The servers restart every day at 3 AM UTC