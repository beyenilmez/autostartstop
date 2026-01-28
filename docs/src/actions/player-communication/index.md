# Player Communication Actions

Actions for sending messages, action bars, titles, and bossbars to players.

## Available actions
<!-- include-start -->
- **[send_message](send-message.md)**: Send a message to one or more players
- **[send_action_bar](send-action-bar.md)**: Send an action bar message to one or more players
- **[send_title](send-title.md)**: Send a title to one or more players
- **[clear_title](clear-title.md)**: Clear title of one or more players
- **[show_bossbar](show-bossbar.md)**: Show a bossbar to one or more players
- **[hide_bossbar](hide-bossbar.md)**: Hide a bossbar from one or more players
<!-- include-end -->

## Player targeting

These actions support targeting players in multiple ways:

| Parameter   | Description                                         |
|-------------|-----------------------------------------------------|
| `player`    | Single player (player name or UUID)                 |
| `players`   | Multiple players (list of player names/UUIDs)       |
| `server`    | All players on a single server                      |
| `servers`   | All players on multiple servers                     |

At least one targeting parameter must be provided.
