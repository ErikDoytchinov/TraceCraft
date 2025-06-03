package eu.doytchinov.tracecraft.events;

import com.google.gson.JsonObject;

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
}