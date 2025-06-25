package eu.doytchinov.tracecraft.events;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.config.ConfigHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.UUID;

public class Event {
    private final JsonObject json;
    private final String event;
    private final long timestamp;

    public Event(JsonObject j, String event) {
        this.json = j;
        this.event = event;
        this.timestamp = System.currentTimeMillis();
    }

    public String getEvent() {
        return event;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getString(String k) {
        return json.get(k).getAsString();
    }

    public long getLong(String k) {
        return json.get(k).getAsLong();
    }

    public double getDouble(String k) {
        return json.get(k).getAsDouble();
    }

    public JsonObject getJson() {
        return json;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    public static void sendEvent(String type, JsonObject payload) {
        // In dry-run mode, log the event instead of queuing it
        if (ConfigHandler.isDryRunMode()) {
            Logger logger = LogManager.getLogger();
            logger.info("[DRY-RUN] Event: {} - Data: {}", type, payload.toString());
            return;
        }

        TraceCraft.QUEUE.addEvent(new Event(payload, type));
    }

    public static JsonObject createPlayerPayload(UUID playerId) {
        JsonObject o = new JsonObject();
        o.addProperty("player", playerId.toString());
        return o;
    }
}