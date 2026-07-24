package cse311.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe signal/dispatcher pattern mirroring Gallant::Signal from VSRTL.
 * Allows background CPU threads to notify UI listeners of state changes.
 */
public class Signal {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void connect(Runnable listener) {
        listeners.add(listener);
    }

    public void disconnect(Runnable listener) {
        listeners.remove(listener);
    }

    public void emit() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public void clear() {
        listeners.clear();
    }
}
