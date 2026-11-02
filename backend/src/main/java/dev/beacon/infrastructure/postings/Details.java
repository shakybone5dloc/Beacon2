package dev.beacon.infrastructure.postings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * Fills in search results one detail call each, a few at a time so a careers site
 * isn't hit with a burst. A result whose detail call fails is kept as it was.
 */
final class Details {

    private static final Logger log = LoggerFactory.getLogger(Details.class);

    /** At most this many detail calls to one host at once. */
    static final int PARALLEL = 4;

    private Details() {}

    static <T> List<T> fill(List<T> results, Function<T, T> detail) {
        var permits = new Semaphore(PARALLEL);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<T>>();
            for (var result : results) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return detail.apply(result);
                    } finally {
                        permits.release();
                    }
                }));
            }
            var filled = new ArrayList<T>();
            for (int i = 0; i < results.size(); i++) {
                try {
                    filled.add(futures.get(i).get());
                } catch (ExecutionException ex) {
                    log.debug("Detail call failed; keeping the search result", ex.getCause());
                    filled.add(results.get(i));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    filled.add(results.get(i));
                }
            }
            return filled;
        }
    }
}
