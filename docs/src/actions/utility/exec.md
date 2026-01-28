# exec Action

Executes a shell command with optional working directory, timeout, and environment variables.

## Configuration

```{ .yaml }
action:
  - exec:
      command: './backup.sh'             # Command to execute
      working_directory: '/opt/scripts'  # Working directory
      timeout: 5m                        # Command timeout
      environment:                       # Environment variables
        BACKUP_DIR: '/backups'
        SERVER_NAME: survival
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `command` | - | The shell command to execute |
| `working_directory` | - | The working directory for the command |
| `timeout` | - | Maximum time to wait for command completion (e.g., `5m`, `30s`) |
| `environment` | - | Environment variables as key-value pairs |

## Example

```{ .yaml }
rules:
  exec:
    triggers:
      - manual:
          id: 'backup'  # Trigger with: /autostartstop trigger backup
    action:
      - exec:
          command: './backup.sh'  # Backup script
          working_directory: '/opt/minecraft/scripts'  # Script directory
          timeout: 10m  # Maximum execution time
          environment:
            SERVER_NAME: survival  # Environment variable
            BACKUP_DIR: '/backups/survival'  # Backup directory
```
This rule will execute a backup script with the specified environment variables.