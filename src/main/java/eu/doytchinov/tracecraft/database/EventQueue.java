package eu.doytchinov.tracecraft.database;

import eu.doytchinov.tracecraft.events.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class EventQueue {
    private static final int CAP = 1000;
    private final LinkedBlockingQueue<Event> q = new LinkedBlockingQueue<>(CAP);

    public void addEvent(Event e){ q.offer(e); }

    public List<Event> drain(int max){
        List<Event> out = new ArrayList<>();
        q.drainTo(out, max);
        return out;
    }
}