package eu.doytchinov.tracecraft.database;

import eu.doytchinov.tracecraft.events.Event;
import eu.doytchinov.tracecraft.config.ConfigHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventQueue {
    private final ConcurrentLinkedQueue<Event> q;

    public EventQueue() {
        this.q = new ConcurrentLinkedQueue<>();
    }

    public void addEvent(Event e) {
        q.add(e); // Non-blocking add
    }

    public List<Event> drain(int max) {
        List<Event> out = new ArrayList<>();
        for (int i = 0; i < max && !q.isEmpty(); i++) {
            Event e = q.poll(); // Non-blocking poll
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return q.isEmpty();
    }

    public int size() {
        return q.size();
    }

    public boolean shouldFlush() {
        try {
            return q.size() >= ConfigHandler.getFlushThreshold();
        } catch (Exception e) {
            return q.size() >= 100;
        }
    }
}