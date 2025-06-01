package eu.doytchinov.tracecraft.influxdb;

import eu.doytchinov.tracecraft.TraceCraft;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.BatchPoints;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InfluxDBHelper implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(InfluxDBHelper.class.getName());
    private final InfluxDB influxDB;
    private final String bucketName = "minecraft_metrics";
    private final String retentionPolicy = "autogen";

    private static final String INFLUXDB_URL = "http://localhost:8086";
    private static final String TOKEN = "Ri0u5ZwseTvdFHWrDKfH5fZVzL04tZkMiVzlIhWm5r3DazI2it2AweHUEZrPjDs1w7UMOfpgOxZRtc__D1gX7w==";
    private static final String ORG = "minecraft-tracecraft";

    public InfluxDBHelper() {
        try {
            this.influxDB = InfluxDBFactory.connect(INFLUXDB_URL, ORG, TOKEN);
            if (!this.influxDB.ping().isGood()) {
                throw new RuntimeException("Failed to connect to InfluxDB or ping was unsuccessful.");
            }
            LOGGER.info("InfluxDB connection initialised. URL: " + INFLUXDB_URL + ", Bucket: " + bucketName);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "InfluxDB init failed", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        var batch = TraceCraft.QUEUE.drain(256);
        if (batch.isEmpty()) {
            return;
        }

        BatchPoints.Builder batchPointsBuilder = BatchPoints
                .database(bucketName)
                .retentionPolicy(retentionPolicy);

        for (eu.doytchinov.tracecraft.events.Event ev : batch) {
            Point.Builder pointBuilder = Point.measurement(ev.getEvent())
                    .time(ev.getTimestamp(), TimeUnit.MILLISECONDS);

            String eventType = ev.getEvent();

            try {
                switch (eventType) {
                    case "player_count":
                        pointBuilder.addField("count", ev.getLong("count"));
                        break;
                    case "player_chunk_margin":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("distance_chunks", ev.getLong("distance_chunks"));
                        pointBuilder.addField("distance_blocks", ev.getLong("distance_blocks"));
                        break;
                    case "block_place":
                    case "block_break":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.tag("block", ev.getString("block"));
                        pointBuilder.addField("x", ev.getLong("x"));
                        pointBuilder.addField("y", ev.getLong("y"));
                        pointBuilder.addField("z", ev.getLong("z"));
                        break;
                    case "login":
                    case "logout":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("event", 1);
                        break;
                    case "tps":
                        pointBuilder.addField("value", ev.getLong("value"));
                        break;
                    case "client_metrics":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("fps", ev.getLong("fps"));
                        pointBuilder.addField("mem", ev.getLong("mem"));
                        break;
                    case "tick_metrics":
                        pointBuilder.addField("duration_ms", ev.getDouble("duration_ms"));
                        pointBuilder.addField("isr", ev.getDouble("isr"));
                        break;
                    case "physics_event":
                        pointBuilder.tag("block", ev.getString("block"));
                        pointBuilder.addField("x", ev.getLong("x"));
                        pointBuilder.addField("y", ev.getLong("y"));
                        pointBuilder.addField("z", ev.getLong("z"));
                        break;
                    case "player_network_stats":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("queued_messages", ev.getLong("queued_messages"));
                        pointBuilder.addField("queued_bytes", ev.getLong("queued_bytes"));
                        pointBuilder.addField("packets_per_second", ev.getDouble("packets_per_second"));
                        pointBuilder.addField("average_packet_size_bytes", ev.getDouble("average_packet_size_bytes"));
                        break;
                    default:
                        pointBuilder.addField("json_data", ev.toString());
                        LOGGER.fine("Event type '" + eventType + "' stored as raw JSON.");
                        break;
                }
                batchPointsBuilder.point(pointBuilder.build());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to parse fields for event type '" + eventType
                        + "'. Storing as raw JSON. Error: " + e.getMessage(), e);
                Point.Builder fallbackPointBuilder = Point.measurement(ev.getEvent())
                        .time(ev.getTimestamp(), TimeUnit.MILLISECONDS)
                        .addField("json_data", ev.toString())
                        .tag("parsing_error", "true");
                try {
                    String player = ev.getString("player");
                    if (player != null && !player.isEmpty()) {
                        fallbackPointBuilder.tag("player", player);
                    }
                } catch (Exception playerTagEx) {
                    LOGGER.log(Level.WARNING, "Failed to parse player tag for event type '" + eventType
                            + "'. Error: " + playerTagEx.getMessage(), playerTagEx);
                }
                batchPointsBuilder.point(fallbackPointBuilder.build());
            }
        }

        try {
            influxDB.write(batchPointsBuilder.build());
            LOGGER.fine("Successfully wrote " + batch.size() + " points to InfluxDB.");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error writing batch to InfluxDB. Batch size: " + batch.size(), ex);
        }
    }

    public void close() {
        try {
            if (this.influxDB != null) {
                this.influxDB.close();
                LOGGER.info("InfluxDB connection closed.");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error closing InfluxDB connection", ex);
        }
    }
}