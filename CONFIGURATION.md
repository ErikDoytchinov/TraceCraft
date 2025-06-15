# TraceCraft Configuration Guide

## InfluxDB Configuration

The TraceCraft mod uses InfluxDB to store metrics collected during gameplay. You can configure the connection details by editing the `config/tracemod-config.yaml` file.

### Configuration Options:

```yaml
influxdb:
    url: "http://localhost:8086" # The URL of your InfluxDB server
    token: "your-influxdb-token" # API token for authentication
    organization: "your-organization" # Your InfluxDB organization name
    bucket: "minecraft_metrics" # The bucket to store data in
    retentionPolicy: "autogen" # Retention policy for your data
metrics:
    enabled: true # Whether metrics collection is enabled
```

### How to Change InfluxDB Server:

1. Open the configuration file at `config/tracemod-config.yaml`
2. Locate the `url` parameter under the `influxdb` section
3. Replace the URL with your target InfluxDB server
4. Save the file and restart your Minecraft server

### Default Values:

If the configuration file is missing or any value is not specified, the mod will use these defaults:

- URL: http://localhost:8086
- Organization: minecraft-tracecraft
- Bucket: minecraft_metrics
- Retention Policy: autogen

## Troubleshooting

If you encounter connection issues:

1. Verify the InfluxDB server is running and accessible
2. Check the server logs for any connection errors
3. Ensure your token has proper permissions to write to the specified bucket
4. If using a remote server, verify network connectivity and firewall rules
