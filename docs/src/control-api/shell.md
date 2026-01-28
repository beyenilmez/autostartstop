# Shell Control API

Use shell commands to start, stop restart servers and send commands to the server console.

## Configuration

```{ .yaml }
servers:
  lobby:
    control_api:
      type: 'shell'
      start_command: './start.sh'
      stop_command: './stop.sh'
      restart_command: './restart.sh'
      send_command_command: './send.sh "${command}"'
      working_directory: '/path/to/server'
      command_timeout: 60s
      environment:
        JAVA_HOME: '/usr/lib/jvm/java-17'
        SERVER_NAME: 'lobby'
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `type` | - | Must be `shell` |
| `start_command` | - | Command to start the server |
| `stop_command` | - | Command to stop the server |
| `restart_command` | - | Command to restart the server |
| `send_command_command` | - | Command template for sending commands to server console. Use `${command}` placeholder for the actual command |
| `working_directory` | Current directory | Working directory for commands |
| `command_timeout` | `60s` | Timeout for commands (e.g., `500ms`, `30s`, `2m`) |
| `environment` | - | Environment variables to set when executing commands |

## Examples

### Minimal configuration

```{ .yaml }
servers:
  survival:
    control_api:
      type: 'shell'
      start_command: './start.sh'
      stop_command: './stop.sh'
      restart_command: './stop.sh && ./start.sh'
      send_command_command: './send.sh "${command}"'
      working_directory: '/home/minecraft/survival'
```

### Using systemd

```{ .yaml }
servers:
  survival:
    control_api:
      type: 'shell'
      start_command: 'systemctl start minecraft-survival'
      stop_command: 'systemctl stop minecraft-survival'
      restart_command: 'systemctl restart minecraft-survival'
```

### With environment variables

```{ .yaml }
servers:
  lobby:
    control_api:
      type: 'shell'
      start_command: './start.sh'
      stop_command: './stop.sh'
      working_directory: '/home/minecraft/lobby'
      environment:
        JAVA_HOME: '/usr/lib/jvm/java-17'
        SERVER_NAME: 'lobby'
```