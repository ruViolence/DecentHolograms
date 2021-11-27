package eu.decentsoftware.holograms.api.utils.tick;

import eu.decentsoftware.holograms.api.utils.DExecutor;
import eu.decentsoftware.holograms.api.utils.collection.DList;
import eu.decentsoftware.holograms.api.utils.scheduler.S;

import java.util.concurrent.atomic.AtomicLong;

public class Ticker {

    private final DList<ITicked> tickedObjects;
    private final DList<ITicked> newTickedObjects;
    private final DList<String> removeTickedObjects;
    private final AtomicLong ticks;
    private volatile boolean performingTick;
    private final int taskId;

    public Ticker() {
        this.tickedObjects = new DList<>(1024);
        this.newTickedObjects = new DList<>(64);
        this.removeTickedObjects = new DList<>(64);
        this.ticks = new AtomicLong(0);
        this.performingTick = false;
        this.taskId = S.asyncTask(() -> {
            if (!performingTick) tick();
        }, 1L).getTaskId();
    }

    public void destroy() {
        S.stopTask(taskId);
        tickedObjects.clear();
        newTickedObjects.clear();
        removeTickedObjects.clear();
    }

    public void register(ITicked ticked) {
        if (tickedObjects.contains(ticked)) return;
        synchronized (newTickedObjects) {
            if (!newTickedObjects.contains(ticked)) {
                newTickedObjects.add(ticked);
            }
        }
    }

    public void unregister(String id) {
        synchronized (removeTickedObjects) {
            if (!removeTickedObjects.contains(id)) {
                removeTickedObjects.add(id);
            }
        }
    }

    private void tick() {
        performingTick = true;

        // Tick all ticked objects
        DExecutor e = DExecutor.create(tickedObjects.size());
        synchronized (tickedObjects) {
            for (ITicked ticked : tickedObjects) {
                e.queue(() -> {
                    if (ticked.shouldTick(ticks.get())) {
                        try {
                            ticked.tick();
                        } catch(Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            }
        }

        // Add new ticked objects
        synchronized (newTickedObjects) {
            while (newTickedObjects.hasElements()) {
                tickedObjects.add(newTickedObjects.pop());
            }
        }

        // Remove ticked objects
        synchronized(removeTickedObjects) {
            while(removeTickedObjects.hasElements()) {
                String id = removeTickedObjects.popRandom();
                for (int i = 0; i < tickedObjects.size(); i++) {
                    if(tickedObjects.get(i).getId().equals(id)) {
                        tickedObjects.remove(i);
                        break;
                    }
                }
            }
        }
        performingTick = false;
        ticks.incrementAndGet();
    }

}
