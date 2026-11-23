package sb.broker.bff.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sb.broker.bff.config.properties.CacheProperties;

import java.util.Optional;

/**
 * Локальные кэши пода с метриками Micrometer ({@code cache.gets{cache=<имя>,result=hit|miss}} и др.).
 * Выключенный кэш ({@code ttl = 0}) — пустой {@link Optional}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalCaches {

    private final MeterRegistry meterRegistry;

    public <K, V> Optional<Cache<K, V>> create(String name, CacheProperties.Spec spec) {
        if (!spec.enabled()) {
            log.info("Cache disabled: name={}", name);
            return Optional.empty();
        }
        log.info("Cache created: name={}, ttl={}, maxSize={}", name, spec.ttl(), spec.maxSize());
        Cache<K, V> cache = Caffeine.newBuilder()
                .expireAfterWrite(spec.ttl())
                .maximumSize(spec.maxSize())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, name);
        return Optional.of(cache);
    }
}
