# Actions

Actions define **what** should happen when a rule fires. A rule can have multiple actions, and they execute sequentially.

## Action execution

Actions execute sequentially in the order they are defined. By default, actions execute synchronously. You can use `wait_for_completion: false` on individual actions to execute them asynchronously.

## Available actions

### [Server Management](server-management/index.md)
Actions for managing server lifecycle: starting, stopping, and restarting servers.
{%
    include-markdown "./server-management/index.md"
    start="<!-- include-start -->"
    end="<!-- include-end -->"
%}

### [Player Management](player-management/index.md)
Actions for managing player connections and disconnections.
{%
    include-markdown "./player-management/index.md"
    start="<!-- include-start -->"
    end="<!-- include-end -->"
%}

### [Player Communication](player-communication/index.md)
Actions for sending messages, action bars, titles, and bossbars to players.
{%
    include-markdown "./player-communication/index.md"
    start="<!-- include-start -->"
    end="<!-- include-end -->"
%}

### [Ping Management](ping-management/index.md)
Actions for managing ping requests and customizing server list responses.
{%
    include-markdown "./ping-management/index.md"
    start="<!-- include-start -->"
    end="<!-- include-end -->"
%}

### [Control Flow](control-flow/index.md)
Actions for conditional execution and looping.
{%
    include-markdown "./control-flow/index.md"
    start="<!-- include-start -->"
    end="<!-- include-end -->"
%}

### [Utility](utility/index.md)
General-purpose utility actions for logging, waiting, and executing commands.
{%
    include-markdown "./utility/index.md"
    start="<!-- include-start -->"
    end="<!-- include-end -->"
%}