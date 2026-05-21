package com.pinkyudeer.tasket.db;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;
import com.pinkyudeer.tasket.Tasket;

public final class AsyncSqlExecutor {

    public static final AsyncSqlExecutor INSTANCE = new AsyncSqlExecutor();

    private static final long CACHE_TTL_MS = 7_500L;
    private static final long IN_FLIGHT_REUSE_MS = 200L;

    private final Object executorLock = new Object();
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final Map<String, InFlightEntry<?>> inFlight = new ConcurrentHashMap<>();
    private ExecutorService executor = newExecutor();

    private AsyncSqlExecutor() {}

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> submit(String key, Set<String> tables, Supplier<T> supplier) {
        if (key == null || key.isEmpty()) key = "anonymous:" + System.nanoTime();
        long now = System.currentTimeMillis();
        CacheEntry<?> cached = cache.get(key);
        if (cached != null && now - cached.createdAt <= CACHE_TTL_MS) {
            return CompletableFuture.completedFuture((T) cached.value);
        }

        InFlightEntry<?> current = inFlight.get(key);
        if (current != null && (!current.future.isDone() || now - current.createdAt <= IN_FLIGHT_REUSE_MS)) {
            return (CompletableFuture<T>) current.future;
        }

        String finalKey = key;
        CompletableFuture<T> future = new CompletableFuture<>();
        inFlight.put(finalKey, new InFlightEntry<>(future, now));
        executor().submit(() -> {
            try {
                Thread.sleep(IN_FLIGHT_REUSE_MS);
                future.complete(supplier.get());
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                future.completeExceptionally(e);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.whenComplete((value, error) -> {
            inFlight.remove(finalKey);
            if (error == null) {
                cache.put(finalKey, new CacheEntry<>(value, System.currentTimeMillis(), tables));
            } else {
                Tasket.LOG.error("Async SQL task failed: {}", finalKey, error);
            }
        });
        return future;
    }

    public void invalidate(String table) {
        if (table == null || table.isEmpty()) return;
        Iterator<Map.Entry<String, CacheEntry<?>>> iterator = cache.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry<?>> entry = iterator.next();
            if (entry.getValue().tables != null && entry.getValue().tables.contains(table)) {
                iterator.remove();
            }
        }
    }

    public void shutdown() {
        cache.clear();
        IllegalStateException closed = new IllegalStateException("SQLite executor closed");
        for (InFlightEntry<?> entry : inFlight.values()) {
            entry.future.completeExceptionally(closed);
        }
        inFlight.clear();
        synchronized (executorLock) {
            executor.shutdownNow();
        }
    }

    private ExecutorService executor() {
        synchronized (executorLock) {
            if (executor.isShutdown() || executor.isTerminated()) {
                executor = newExecutor();
            }
            return executor;
        }
    }

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Tasket-SQL");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Desugar
    private record CacheEntry<T> (T value, long createdAt, Set<String> tables) {}

    @Desugar
    private record InFlightEntry<T> (CompletableFuture<T> future, long createdAt) {}
}
