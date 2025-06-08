package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import java.util.UUID;

public class EventHelper {

    /**
     * Sends an event with specified type and JSON payload.
     */
    public static void sendEvent(String type, JsonObject payload) {
        TraceCraft.QUEUE.addEvent(new Event(payload, type));
    }

    /**
     * Creates a basic JSON payload with player UUID.
     */
    public static JsonObject createPlayerPayload(UUID playerId) {
        JsonObject o = new JsonObject();
        o.addProperty("player", playerId.toString());
        return o;
    }
}
