package com.fares.demo1.service;

import com.fares.demo1.config.EndpointPolicy;
import com.fares.demo1.config.EndpointPolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pure unit test - no Spring context, no database, no real waiting for a TTL to
 * expire. {@link EndpointPolicyRegistry} is mocked so each test controls the policy
 * directly instead of going through the admin API; the loader is a counter so "did the
 * cache actually skip a reload" is a plain assertion rather than something inferred
 * from timestamps like the live curl checks used during development.
 */
@ExtendWith(MockitoExtension.class)
class MetricCacheTest {

    @Mock
    private EndpointPolicyRegistry endpointPolicyRegistry;

    private MetricCache metricCache;

    @BeforeEach
    void setUp() {
        metricCache = new MetricCache(endpointPolicyRegistry);
    }

    @Test
    void cachedTrue_secondCallWithinTtlDoesNotReloadOrChangeTheAnswer() {
        when(endpointPolicyRegistry.get("k")).thenReturn(new EndpointPolicy(true, false));
        AtomicInteger loads = new AtomicInteger();

        Object first = metricCache.getOrLoad("k", Duration.ofMinutes(1), () -> "value-" + loads.incrementAndGet());
        Object second = metricCache.getOrLoad("k", Duration.ofMinutes(1), () -> "value-" + loads.incrementAndGet());

        assertThat(first).isEqualTo("value-1");
        assertThat(second).isEqualTo("value-1");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void cachedFalse_everyCallReloads() {
        when(endpointPolicyRegistry.get("k")).thenReturn(new EndpointPolicy(false, false));
        AtomicInteger loads = new AtomicInteger();

        metricCache.getOrLoad("k", Duration.ofMinutes(1), loads::incrementAndGet);
        metricCache.getOrLoad("k", Duration.ofMinutes(1), loads::incrementAndGet);

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void unknownMetricKey_bypassesTheCacheJustLikeCachedFalse() {
        when(endpointPolicyRegistry.get("nope")).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        metricCache.getOrLoad("nope", Duration.ofMinutes(1), loads::incrementAndGet);
        metricCache.getOrLoad("nope", Duration.ofMinutes(1), loads::incrementAndGet);

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void turningCachingOffThenBackOnDoesNotResurfaceTheOldAnswer() {
        AtomicInteger loads = new AtomicInteger();

        when(endpointPolicyRegistry.get("k")).thenReturn(new EndpointPolicy(true, false));
        Object whileCached = metricCache.getOrLoad("k", Duration.ofMinutes(1), () -> "answer-" + loads.incrementAndGet());
        assertThat(whileCached).isEqualTo("answer-1");

        // admin turns caching off - bypasses AND evicts the entry from answer-1
        when(endpointPolicyRegistry.get("k")).thenReturn(new EndpointPolicy(false, false));
        Object whileDisabled = metricCache.getOrLoad("k", Duration.ofMinutes(1), () -> "answer-" + loads.incrementAndGet());
        assertThat(whileDisabled).isEqualTo("answer-2");

        // admin turns caching back on - must load fresh, not resurrect answer-1
        when(endpointPolicyRegistry.get("k")).thenReturn(new EndpointPolicy(true, false));
        Object afterReenable = metricCache.getOrLoad("k", Duration.ofMinutes(1), () -> "answer-" + loads.incrementAndGet());
        assertThat(afterReenable).isEqualTo("answer-3");
    }

    @Test
    void differentMetricKeysAreCachedIndependently() {
        when(endpointPolicyRegistry.get("a")).thenReturn(new EndpointPolicy(true, false));
        when(endpointPolicyRegistry.get("b")).thenReturn(new EndpointPolicy(true, false));

        Object a = metricCache.getOrLoad("a", Duration.ofMinutes(1), () -> "A");
        Object b = metricCache.getOrLoad("b", Duration.ofMinutes(1), () -> "B");

        assertThat(a).isEqualTo("A");
        assertThat(b).isEqualTo("B");
    }
}
