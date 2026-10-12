package co.proxychecker.components.net;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a running check abort its network I/O. Blocking socket calls don't react to thread interrupts,
 * so connections opened while a cancellation is {@linkplain #runWith bound to the thread} are
 * registered here and closed by {@link #cancel()}, which makes any blocked connect or read fail at once
 * instead of waiting for its timeout.
 */
public final class Cancellation {

    private static final ThreadLocal<Cancellation> CURRENT = new ThreadLocal<>();

    private final Set<Closeable> open = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    /**
     * @return the cancellation bound to the calling thread, or null
     */
    public static Cancellation current() {
        return CURRENT.get();
    }

    /**
     * Runs {@code task} with this cancellation bound to the calling thread.
     */
    public void runWith(Runnable task) {
        Cancellation previous = CURRENT.get();
        CURRENT.set(this);
        try {
            task.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Tracks a resource so {@link #cancel()} can close it.
     *
     * @throws CancelledException if already cancelled; the resource is closed first
     */
    public void register(Closeable resource) throws CancelledException {
        open.add(resource);
        if (cancelled) {
            // cancel() may have run before the add, so close it here as well
            open.remove(resource);
            closeQuietly(resource);
            throw new CancelledException();
        }
    }

    public void unregister(Closeable resource) {
        open.remove(resource);
    }

    /**
     * Marks this cancelled and closes every registered resource. Safe to call more than once.
     */
    public void cancel() {
        cancelled = true;
        for (Closeable resource : open) {
            open.remove(resource);
            closeQuietly(resource);
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private static void closeQuietly(Closeable resource) {
        try {
            resource.close();
        } catch (IOException ignored) {
            // closing only to abort; nothing useful to do
        }
    }

    /**
     * Thrown when work is attempted after cancellation.
     */
    public static final class CancelledException extends IOException {
        private static final long serialVersionUID = 1L;

        public CancelledException() {
            super("Cancelled");
        }
    }
}
