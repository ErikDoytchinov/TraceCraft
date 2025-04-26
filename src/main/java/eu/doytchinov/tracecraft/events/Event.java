package eu.doytchinov.tracecraft.events;

import com.google.gson.JsonObject;

public class Event {
    private final JsonObject json;

    public Event(JsonObject j){
        this.json = j;
    }

    public String getString(String k){ return json.get(k).getAsString(); }
    public long   getLong  (String k){ return json.get(k).getAsLong(); }
    @Override public String toString(){ return json.toString(); }
}