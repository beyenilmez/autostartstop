# Server Management Actions

Actions for managing server lifecycle: starting, stopping, and restarting servers.

!!! requirement "Control API required"
    All server management actions require:

    - Server configuration in the [`servers:`](/configuration/servers.md) section of `config.yml`
    - A configured [control API](/control-api/index.md)
    
    See [Servers](/configuration/servers.md) for configuration details.

## Available actions
<!-- include-start -->
- **[start](start.md)**: Start a server
- **[stop](stop.md)**: Stop a server
- **[restart](restart.md)**: Restart a server
- **[send_command](send-command.md)**: Send a command to a server's console
<!-- include-end -->