package eu.doytchinov.tracecraft.database;

import eu.doytchinov.tracecraft.events.Event;
import eu.doytchinov.tracecraft.config.ConfigHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class EventQueue {
    private final LinkedBlockingQueue<Event> q;

    public EventQueue() {
        // initialize with configurable capacity, fallback to 1000 if config not available
        int capacity;
        try {
            capacity = ConfigHandler.getQueueSize();
        } catch (Exception e) {
            capacity = 1000;
        }
        this.q = new LinkedBlockingQueue<>(capacity);
    }

    public void addEvent(Event e) {
        q.offer(e);
    }

    public List<Event> drain(int max) {
        List<Event> out = new ArrayList<>();
        q.drainTo(out, max);
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