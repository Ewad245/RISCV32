package cse311.kernel.lock;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple Spinlock implementation that mimics xv6's spinlock.
 * It uses a test-and-set atomic operation to acquire the lock.
 * <p>
 * In a real OS, this would also need to disable interrupts (push_off/pop_off)
 * to avoid deadlock if an interrupt handler tries to acquire the same lock.
 * For this simulation, we currently focus on the mutual exclusion aspect.
 */
public class Spinlock {
    private final String name;
    private final AtomicBoolean locked;

    // Debugging information
    private Thread owner;

    public Spinlock(String name) {
        this.name = name;
        this.locked = new AtomicBoolean(false);
        this.owner = null;
    }

    /**
     * Acquire the lock.
     * Loops (spins) until the lock is acquired.
     */
    public void acquire() {
        // In xv6: push_off(); // disable interrupts to avoid deadlock

        if (holding()) {
            throw new RuntimeException("Spinlock '" + name + "' acquire: already holding");
        }

        // Test-and-Set loop
        while (!locked.compareAndSet(false, true)) {
            // Spin...
            // In Java, we can yield to be polite to the host OS scheduler
            // though a real spinlock burns CPU.
            Thread.yield();
        }

        // Record info for debugging (and holding() check)
        // In xv6: __sync_synchronize();
        this.owner = Thread.currentThread();
    }

    /**
     * Release the lock.
     */
    public void release() {
        if (!holding()) {
            throw new RuntimeException("Spinlock '" + name + "' release: not holding");
        }

        this.owner = null;
        // In xv6: __sync_synchronize();

        // Release the lock
        locked.set(false);

        // In xv6: pop_off(); // enable interrupts
    }

    /**
     * Check if the current thread/cpu holds the lock.
     */
    public boolean holding() {
        return locked.get() && owner == Thread.currentThread();
    }

    public String getName() {
        return name;
    }
}
