# start_on_connection Template

Automatically starts a server when a player attempts to connect to it. Supports multiple connection handling modes for different user experiences.

## Configuration

```{ .yaml }
rules:
  auto_start:
    template: start_on_connection      # Template type
    servers: ['survival', 'creative']  # List of server names to monitor
    players: ['alice', 'bob']          # Optional: Filter by player names
    mode: waiting_server  # Connection handling mode: none, disconnect, hold, waiting_server
    # ... mode-specific configuration
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `servers` | - | List of server names to monitor. At least one server must be specified. |
| `players` | - | Optional list of player names to filter. If specified, only these players will trigger the template. |
| `mode` | `none` | Connection handling mode. See [Connection modes](#connection-modes) for details. |
| `disconnect_message` | `"<gold>${connection.server.name}</gold> is currently <gray>${${connection.server.name}.state}</gray>. Try again in a few seconds."` | Message to show when disconnecting player (for `disconnect` mode). Supports variables. |
| `waiting_server` | - | Configuration for `waiting_server` mode. See [Waiting server configuration](#waiting-server-configuration). |

## Connection modes

### `none`

Just starts the server without interfering with the connection. The player's connection attempt will fail if the server is offline, but the server will start in the background.

```{ .yaml }
rules:
  start_background:
    template: start_on_connection
    servers: ['survival']
    mode: none
```

### `disconnect`

Denies the connection, starts the server, and disconnects the player with a message.

```{ .yaml }
rules:
  start_and_disconnect:
    template: start_on_connection
    servers: ['survival']
    mode: disconnect
    disconnect_message: "<gold>${connection.server.name}</gold> is starting. Try again in a few seconds."
```

### `hold`

Denies the connection, starts the server, waits for it to come online, then allows the connection.

!!! warning "Hold mode timeout"
    Hold mode has a 30-second timeout. Only use this mode for servers that start quickly (under 30 seconds). For slower servers, use `waiting_server` mode instead.

```{ .yaml }
rules:
  start_and_wait:
    template: start_on_connection
    servers: ['survival']
    mode: hold
```

### `waiting_server`

The most sophisticated mode. Denies the connection, sends the player to a waiting server (like a limbo), starts the target server, shows progress UI (title, bossbar, action bar), then connects the player when ready.

```{ .yaml }
rules:
  start_with_waiting:
    template: start_on_connection
    servers: ['survival']
    mode: waiting_server
    waiting_server:
      server: limbo
      start_waiting_server_on_connection: true
      message:
        enabled: false
        message: "<gold>${connection.server.name}</gold> <gray>is starting. You will be connected shortly.</gray>"
      progress_bar:
        enabled: true
        message: "${${connection.server.name}.startup_progress_percentage}%"
        progress: "${${connection.server.name}.startup_progress}"
        color: WHITE
        overlay: PROGRESS
      title:
        enabled: true
        title: "Please Wait..."
        subtitle: "<gold>${connection.server.name}</gold> <gray>is ${${connection.server.name}.state}. You will be connected shortly.</gray>"
        fade_in: 1s
        stay: 1h
        fade_out: 1s
      action_bar:
        enabled: false
        message: "Server is ${${connection.server.name}.state}"
```

## Waiting server configuration

When using `waiting_server` mode, you can configure the waiting experience:

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | Name of the waiting server, typically a `limbo` server |
| `start_waiting_server_on_connection` | `true` | If `true`, automatically starts the waiting server on connection if it's offline, works like hold mode |
| `message` | - | Message configuration (see [below](#message-configuration)) |
| `progress_bar` | - | Bossbar/progress bar configuration (see [below](#progress-bar-configuration)) |
| `title` | - | Title configuration (see [below](#title-configuration)) |
| `action_bar` | - | Action bar configuration (see [below](#action-bar-configuration)) |

### Message configuration

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `false` | Enable/disable the message |
| `message` | `"<gold>${connection.server.name}</gold> <gray>is starting. You will be connected shortly.</gray>"` | Message to send when player enters waiting server. Supports variables. |

### Progress bar configuration

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `true` | Enable/disable the progress bar |
| `message` | Progress percentage | Bossbar message. Supports variables. |
| `progress` | Startup progress | Progress value (0.0-1.0). Supports variables. |
| `color` | `WHITE` | Bossbar color: `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE` |
| `overlay` | `PROGRESS` | Bossbar overlay: `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20` |

### Title configuration

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `true` | Enable/disable the title |
| `title` | `"Please Wait..."` | Title text. Supports variables. |
| `subtitle` | `"<gold>${connection.server.name}</gold> <gray>is ${${connection.server.name}.state}. You will be connected shortly.</gray>"` | Subtitle text. Supports variables. |
| `fade_in` | `1s` | Fade-in duration |
| `stay` | `1h` | How long the title stays visible |
| `fade_out` | `1s` | Fade-out duration |

### Action bar configuration

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `false` | Enable/disable the action bar |
| `message` | `"Server is ${${connection.server.name}.state}"` | Action bar message. Supports variables. |

## How it works

The `start_on_connection` template internally uses the following components:

- **[connection](/triggers/connection.md) trigger**: Detects when a player attempts to connect to a server.

- **[start](/actions/server-management/start.md) action**: Starts the target server when a connection attempt is detected. The action automatically tracks server startup progress, making startup variables available for use in UI elements.

- **Connection handling actions** (depending on mode):
  - **[allow_connection](/actions/player-management/allow-connection.md)**: Used in `hold` and `waiting_server` modes to allow the connection after the server is ready
  - **[connect](/actions/player-management/connect.md)**: Used in `waiting_server` mode to connect the player to the target server

- **Player communication actions** (for `waiting_server` mode):
  - **[send_message](/actions/player-communication/send-message.md)**: Sends initial message when player enters waiting server
  - **[send_title](/actions/player-communication/send-title.md)**: Shows title with server status updates
  - **[show_bossbar](/actions/player-communication/show-bossbar.md)**: Displays progress bar showing startup progress
  - **[send_action_bar](/actions/player-communication/send-action-bar.md)**: Shows action bar with server status

The template monitors the server startup process and automatically updates UI elements (in `waiting_server` mode) with real-time progress information.

## Available variables

This template provides access to variables from the [connection trigger](/triggers/connection.md#context-variables), which includes player and server information from the connection event. Additionally, you can use [global server variables](/variables.md#global-server-variables) to access server state and startup progress information.

For example:

- `connection.server.name` - The name of the server the player is trying to connect to
- `connection.player.name` - The name of the player attempting to connect
- `${connection.server.name}.state` - Current state of the target server (e.g., `starting`, `online`, `offline`)
- `${connection.server.name}.startup_progress` - Startup progress (0.0-1.0) for progress bars
- `${connection.server.name}.startup_progress_percentage` - Startup progress (0-100) for display

See the [connection trigger documentation](/triggers/connection.md#context-variables) for all available context variables and the [Variables](/variables.md#global-server-variables) page for global server variables.
