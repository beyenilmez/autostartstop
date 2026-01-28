# Pterodactyl Control API

Use Pterodactyl Panel Client API to control servers. This provides integration with Pterodactyl Panel for centralized server management.

## Configuration

```{ .yaml }
servers:
  lobby:
    control_api:
      type: 'pterodactyl'
      panel_url: 'http://localhost/'
      api_key: 'ptlc_xxxxxxxxxxxxx'
      server_id: 'a84e3333'
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `type` | - | Must be `pterodactyl` |
| `panel_url` | - | Base URL of the Pterodactyl panel (e.g., `https://panel.example.com`) |
| `api_key` | - | Client API key (format: `ptlc_...`) |
| `server_id` | - | Server identifier (UUID or short ID) |

## API Key

Pterodactyl uses Client API keys for server management. To create an API key:

1. Log in to your Pterodactyl panel
2. Go to **Account Settings** → **API Credentials**
3. Create and copy the API key (starts with `ptlc_`)

!!! warning
    Copy the API key as soon as you create it, as you will not be able to access it again.

## Server ID

The `server_id` can be either:

- **UUID**: Full server UUID (e.g., `a84e3333-5cb1-4554-8a6a-79b61a77b163`)
- **Short ID**: Server identifier (e.g., `a84e3333`)

You can find the server ID in the Pterodactyl panel URL when viewing a server, or under **Settings** → **Debug Information** → **Server ID** for the server.

## Examples

### Basic Pterodactyl configuration

```{ .yaml }
servers:
  survival:
    control_api:
      type: 'pterodactyl'
      panel_url: 'https://panel.example.com'
      api_key: 'ptlc_xxxxxxxxxxxxx'
      server_id: '87894d2a-e9a6-4302-abdb-3b1a01a5ec40'
```

### Multiple servers with same panel

```{ .yaml }
defaults:
  server:
    control_api:
      type: 'pterodactyl'
      panel_url: 'https://panel.example.com'
      api_key: 'ptlc_xxxxxxxxxxxxx'

servers:
  lobby:
    control_api:
      server_id: 'd3aac109'
  
  survival:
    control_api:
      server_id: 'e4bcd210'
  
  creative:
    control_api:
      server_id: 'f5cde321'
```