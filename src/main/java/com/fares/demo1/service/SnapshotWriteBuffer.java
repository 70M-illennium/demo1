package com.fares.demo1.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Keeps snapshot writes from being lost when the history store is briefly unavailable.
 * A collector hands its {@code repo.save(entity)} call here; if it fails, the write is
 * buffered and retried - oldest first - on the next collector cycle. Buffered entities
 * keep the timestamp they were captured with, so their order in storage stays right.
 *
 * <p>The buffer is in memory, so it does not survive an app restart - that case is
 * covered instead by {@code COLLECTION_GAP} detection. It is bounded; past the cap the
 * oldest buffered rows are dropped.
 */
@Component
@Slf4j
public class SnapshotWriteBuffer {

    /** ~4 hours of buffering across the four collectors before the oldest rows drop. */
    private static final int MAX_BUFFERED = 1000;

    private final Deque<Runnable> pending = new ArrayDeque<>();

    /**
     * Save now if the store is reachable; otherwise buffer and retry later. Also drains
     * anything already buffered before this write.
     */
    public synchronized void save(Runnable saveAction) {
        pending.addLast(saveAction);
        int flushed = drain();

        if (pending.isEmpty()) {
            if (flushed > 1) {
                log.info("history store recovered - flushed {} buffered snapshot(s)", flushed);
            }
            return;
        }
        if (pending.size() > MAX_BUFFERED) {
            int drop = pending.size() - MAX_BUFFERED;
            for (int i = 0; i < drop; i++) {
                pending.pollFirst();
            }
            log.error("snapshot write buffer full - dropped {} oldest buffered row(s)", drop);
        }
        log.warn("history store write failed - {} snapshot(s) buffered for retry", pending.size());
    }

    /** Number of snapshots waiting to be written (0 in normal operation). */
    public synchronized int bufferedCount() {
        return pending.size();
    }

    /** @return how many buffered writes succeeded this pass */
    private int drain() {
        int ok = 0;
        Iterator<Runnable> it = pending.iterator();
        while (it.hasNext()) {
            Runnable save = it.next();
            try {
                save.run();
                it.remove();
                ok++;
            } catch (RuntimeException ex) {
                if (isConnectionProblem(ex)) {
                    break;   // store unreachable - keep this and the rest, retry next cycle
                }
                // a permanent failure (bad column, constraint, ...) would retry forever
                // and hide itself behind "buffered" warnings - drop it and surface it
                log.error("dropping a snapshot write that cannot succeed", ex);
                it.remove();
            }
        }
        return ok;
    }

    /** True if the failure looks like "store is down / connection lost", i.e. worth retrying. */
    private static boolean isConnectionProblem(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLTransientException || t instanceof SQLRecoverableException) {
                return true;
            }
            if (t instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && (state.startsWith("08") || state.startsWith("57"))) {
                    return true;   // 08 = connection exception, 57 = operator intervention
                }
            }
        }
        return false;
    }
}
