package com.fares.demo1.service;

import com.fares.demo1.config.EndpointPolicy;
import com.fares.demo1.config.EndpointPolicyRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Step 2 of the per-metric "speciality" flags: the one place caching actually happens.
 * A read-service method backing a {@code /latest}-style endpoint calls {@link
 * #getOrLoad} instead of querying its repo directly; whether that call is actually
 * served from cache is decided at <em>request time</em> from the live {@link
 * EndpointPolicyRegistry} flag, not from an annotation fixed at compile time - which is
 * exactly why this isn't {@code @Cacheable}: an annotation can't consult a runtime flag.
 *
 * <p>One {@link Cache} is shared across every metric key. Caffeine's usual {@code
 * expireAfterWrite} sets a single TTL for the whole cache, but different metrics need
 * different TTLs (60s for the per-minute snapshots, 10 min for the slower collectors) -
 * so this uses a per-entry {@link Expiry} instead, which reads the TTL each entry was
 * stored with off the {@link Entry} wrapper rather than off the cache itself.
 */
@Component
@RequiredArgsConstructor
public class MetricCache {

    private final EndpointPolicyRegistry endpointPolicyRegistry;

    private final Cache<String, Entry> cache = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, Entry>() {
                @Override
                public long expireAfterCreate(String key, Entry entry, long currentTime) {
                    return entry.ttl().toNanos();
                }

                @Override
                public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
                    return entry.ttl().toNanos();
                }

                @Override
                public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
                    return currentDuration;   // reading a value doesn't push its expiry back
                }
            })
            .build();

    /**
     * Returns the cached value for {@code metricKey} if the policy says to cache it and
     * a fresh-enough entry exists; otherwise calls {@code loader} and, if caching is on
     * for this key, stores the result for {@code ttl} before returning it.
     *
     * <p>When the policy says not to cache, any entry left over from a time it
     * <em>was</em> cached is evicted rather than just ignored - otherwise turning
     * caching back on later could resurface a value that was never refreshed during the
     * time it was supposedly turned off.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String metricKey, Duration ttl, Supplier<T> loader) {
        EndpointPolicy policy = endpointPolicyRegistry.get(metricKey);
        if (policy == null || !policy.cached()) {
            cache.invalidate(metricKey);
            return loader.get();
        }
        Entry entry = cache.get(metricKey, key -> new Entry(loader.get(), ttl));
        return (T) entry.value();
    }

    private record Entry(Object value, Duration ttl) {
    }
}
