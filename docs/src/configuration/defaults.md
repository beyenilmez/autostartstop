# Defaults

The `defaults:` section defines global default values that are merged into all server configurations. This allows you to set common settings once instead of repeating them for each server.

## Default structure

```{ .yaml .no-copy }
defaults:
  server:
    control_api: { ... }
    ping: { ... }
    startup_timer: { ... }
```

See the [Servers](servers.md) page for detailed information about these settings.

## How defaults work

Default values are merged with individual server configurations. If a server doesn't specify a value, the default is used. If a server specifies a value, it overrides the default.

Example:

```{ .yaml }
defaults:
  # Define common settings for all servers here
  server:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'instance_management_bot'
      password: 'your_password'

servers:
  # You only need to specify instance_id for these servers since other
  # settings are already defined in the defaults.
  lobby:
    control_api:
      instance_id: 'MinecraftLobby01'
  
  limbo:
    control_api:
      instance_id: 'MinecraftLimbo01'
  
  # You can override the default settings if needed
  survival:
    control_api:
      type: 'shell'
      start_command: './start.sh'
      stop_command: './stop.sh'
      working_directory: '/path/to/server'
```
