package eu.doytchinov.tracecraft.influxdb;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.config.ConfigHandler;
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

    private static final String INFLUXDB_URL = ConfigHandler.getInfluxdbUrl();
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
                        pointBuilder.addField("ping", ev.getLong("ping"));
                        break;
                    case "mod_adoption":
                        pointBuilder.addField("total_players", ev.getLong("total_players"));
                        pointBuilder.addField("players_with_mod", ev.getLong("players_with_mod"));
                        pointBuilder.addField("players_without_mod", ev.getLong("players_without_mod"));
                        pointBuilder.addField("adoption_rate_percent", ev.getDouble("adoption_rate_percent"));
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
                    case "player_path":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.tag("world", ev.getString("world"));
                        pointBuilder.addField("x", ev.getLong("x"));
                        pointBuilder.addField("y", ev.getLong("y"));
                        pointBuilder.addField("z", ev.getLong("z"));
                        break;
                    case "item_use":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("item", ev.getString("item"));
                        break;
                    case "combat_event":
                        pointBuilder.addField("damage", ev.getDouble("damage"));
                        pointBuilder.tag("target_type", ev.getString("target_type"));
                        try {
                            String ap = ev.getString("attacker_player");
                            if (!ap.isEmpty())
                                pointBuilder.tag("attacker_player", ap);
                        } catch (Exception ignored) {
                        }
                        try {
                            String ae = ev.getString("attacker_entity");
                            if (!ae.isEmpty())
                                pointBuilder.tag("attacker_entity", ae);
                        } catch (Exception ignored) {
                        }
                        break;
                    case "player_death":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("x", ev.getLong("x"));
                        pointBuilder.addField("y", ev.getLong("y"));
                        pointBuilder.addField("z", ev.getLong("z"));
                        pointBuilder.tag("cause", ev.getString("cause"));
                        break;
                    case "session_distance":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("distance", ev.getDouble("distance"));
                        break;
                    case "biome_time":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.tag("biome", ev.getString("biome"));
                        pointBuilder.addField("duration_ms", ev.getLong("duration_ms"));
                        break;
                    case "social_proximity":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("nearby_count", ev.getLong("nearby_count"));
                        pointBuilder.addField("avg_distance", ev.getDouble("avg_distance"));
                        break;
                    case "session_idle":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("idle_ms", ev.getLong("idle_ms"));
                        break;
                    case "player_network_stats":
                        pointBuilder.tag("player", ev.getString("player"));
                        pointBuilder.addField("queued_messages", ev.getLong("queued_messages"));
                        pointBuilder.addField("queued_bytes", ev.getLong("queued_bytes"));
                        pointBuilder.addField("packets_per_second", ev.getDouble("packets_per_second"));
                        pointBuilder.addField("average_packet_size_bytes", ev.getDouble("average_packet_size_bytes"));
                        break;
                    case "system_metrics":
                        pointBuilder.addField("max_heap_mb", ev.getLong("max_heap_mb"));
                        pointBuilder.addField("allocated_heap_mb", ev.getLong("allocated_heap_mb"));
                        pointBuilder.addField("used_heap_mb", ev.getLong("used_heap_mb"));
                        pointBuilder.addField("free_heap_mb", ev.getLong("free_heap_mb"));
                        pointBuilder.addField("thread_count", ev.getLong("thread_count"));
                        break;
                    case "entity_count":
                        pointBuilder.tag("level_name", ev.getString("level_name"));
                        pointBuilder.addField("total_entities", ev.getLong("total_entities"));
                        JsonObject entityTypes = ev.getJson().getAsJsonObject("entity_types");
                        if (entityTypes != null) {
                            for (String entityType : entityTypes.keySet()) {
                                pointBuilder.addField(entityType, entityTypes.get(entityType).getAsInt());
                            }
                        }
                        break;
                    case "lighting_update_world_load":
                        pointBuilder.tag("world", ev.getString("world"));
                        pointBuilder.addField("event", 1);
                        break;
                    case "chunk_generation":
                        pointBuilder.tag("world", ev.getString("world"));
                        pointBuilder.addField("chunk_x", ev.getLong("chunk_x"));
                        pointBuilder.addField("chunk_z", ev.getLong("chunk_z"));
                        pointBuilder.addField("generation_time_ms", ev.getDouble("generation_time_ms"));
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