# AMP Control API

Use AMP API to control servers. This provides integration with CubeCoders&#8482; AMP software for centralized server management.

!!! tip "First time setting up?"
    If you're setting up AMP integration for the first time, see the [AMP Control API User Setup](../guides/amp-user-setup.md) guide for step-by-step instructions on creating a dedicated user and configuring permissions.

## Configuration

```{ .yaml }
servers:
  lobby:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'your_username'
      password: 'your_password'
      token: 'your_2fa_token'
      remember_me: false
      instance_id: 'MinecraftLobby01'
      start: 'instance_and_server'
      stop: 'server'
      instance_start_timeout: 30s
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `type` | - | Must be `amp` |
| `ads_url` | - | URL of the ADS panel (e.g., `http://localhost:8080/`) |
| `username` | - | AMP panel username |
| `password` | - | AMP panel password |
| `token` | - | 2FA token (if two-factor authentication is enabled) |
| `remember_me` | `false` | Remember login session to avoid re-authentication |
| `instance_id` | - | Instance name or UUID as configured in AMP |
| `start` | `instance_and_server` | Start mode: `instance_and_server` or `server` |
| `stop` | `server` | Stop mode: `instance_and_server` or `server` |
| `instance_start_timeout` | `30s` | Timeout for instance startup (e.g., `30s`, `1m`) |

## Instance ID

The `instance_id` can be either:

- **UUID**: Full instance UUID (e.g., `31d9266e-8b92-44dc-8098-ddc85815f20a`)
- **Name**: Instance name (e.g., `MinecraftLobby01`)

You can find the instance ID in the AMP panel:

- **UUID**: Go to **Support and Updates** → **InstanceID** for each instance
- **Name**: View the instance name in parentheses next to the friendly name (e.g., `MinecraftLobby01`)

## Start and stop modes

### Start modes

| Mode | Description |
|------|-------------|
| `instance_and_server` | Starts the AMP instance first, then the server. Use this if the instance might be stopped. This is the recommended mode even if the instance is always running. |
| `server` | Starts only the server. Use this if you do not want to start the instance if it is stopped. |

### Stop modes

| Mode | Description |
|------|-------------|
| `instance_and_server` | Stops both the server and the instance. Use this for complete shutdown. |
| `server` | Stops only the server, leaving the instance running. Use this if you want to keep the instance available. This is the recommended mode. |

## Examples

### Basic AMP configuration

```{ .yaml }
servers:
  survival:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'instance_management_bot'
      password: '123456+Abc'
      instance_id: 'eaa348b0-032a-476a-bdc6-6307330876de'
```

### Multiple servers with same panel

```{ .yaml }
defaults:
  server:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'instance_management_bot'
      password: '123456+Abc'

servers:
  lobby:
    control_api:
      instance_id: '5e81b7e1-d7a2-49ae-82c7-0c340c324b61'
  
  survival:
    control_api:
      instance_id: '31d0a69c-9a25-48de-92af-334d475e8bcf'
  
  creative:
    control_api:
      instance_id: '01c46788-d75a-49eb-98fb-b5f3fbe3498f'
```