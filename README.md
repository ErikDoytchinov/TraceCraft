# TraceCraft - Server/Client Metrics Collection

TraceCraft is a Minecraft Forge mod designed to collect gameplay metrics for research purposes. The mod is primarily server-side but can optionally collect additional client-side metrics when installed on both server and client.

## Features

### Server-Side Metrics (Always Collected)

- Player login/logout events
- Player movement and location tracking
- Block placement and breaking
- Combat events
- Server performance (TPS, memory usage, tick duration)
- Player proximity and social interactions
- Biome time tracking
- Chunk loading and generation events
- Entity counts per world

### Client-Side Metrics (Optional)

- Client FPS
- Client memory usage
- Client-server ping
- Only collected when client also has the mod installed

## Installation

### Server Installation (Required)

1. Install Minecraft Forge on your server
2. Place the TraceCraft mod JAR in your server's `mods` folder
3. Configure the mod settings in `config/tracecraft-common.toml`
4. Start your server

### Client Installation (Optional)

1. Install Minecraft Forge
2. Place the same TraceCraft mod JAR in your client's `mods` folder
3. Connect to the server

**Note:** Clients without the mod can still join and play on servers with TraceCraft installed. They will only miss out on client-side metrics collection.

## Configuration

The mod uses InfluxDB to store metrics. Configure your InfluxDB connection in `config/tracecraft-common.toml`:

```toml
[general]
    influxdbUrl = "http://localhost:8086"

[metrics]
    enabled = true
```

## Server Administration

### Commands

Server administrators can use the following commands:

- `/tracecraft status` - Show overall mod adoption statistics
- `/tracecraft players` - List all players and their mod status

### Monitoring

The mod automatically logs client adoption statistics every 5 minutes:

```
TraceCraft Client Adoption: 5/10 players have the client mod (50.0%)
```

## Privacy and Data Collection

This mod collects gameplay data for research purposes. Players are notified upon joining:

- Players with the client mod: Informed that both server and client metrics are collected
- Players without the client mod: Informed that only server-side metrics are collected

All players have the option to disconnect if they prefer not to participate.

## Compatibility

- **Server Version:** Minecraft Forge 1.21.5+
- **Client Version:** Optional - any Minecraft version (without mod) or matching Forge version (with mod)
- **Network Protocol:** Designed to handle version mismatches gracefully

## Technical Details

### Network Protocol

- Uses Forge's networking system with graceful fallback
- Accepts connections from clients without the mod (protocol version 0)
- Clients with the mod use protocol version 1

### Performance Impact

- Minimal server performance impact
- Client metrics sent every 5 seconds (when mod is present)
- Server metrics collection optimized for production use

## Building from Source

```bash
./gradlew build
```

The built JAR will be in `build/libs/tracecraft-1.0.0.jar`

## License

This mod is provided for research purposes. Please review the license file for details.
