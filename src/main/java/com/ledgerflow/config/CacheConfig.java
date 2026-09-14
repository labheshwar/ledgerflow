package com.ledgerflow.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Caching is configured but nothing is currently cached.
 *
 * The balance cache that used to live here was retired when balances became
 * derived: it fronted a single-row lookup, and once that lookup turned into
 * a bounded aggregate Postgres serves from shared buffers, the cache was
 * buying a fraction of a millisecond in exchange for an invalidation hook on
 * every posting and a second place for a cross-tenant key mistake to hide.
 * Removing it deleted more code than it saved.
 *
 * The manager stays because the caches that will earn their place are
 * genuinely expensive -- report aggregates over a full year of entries
 * (milestone 17) -- and because Redis is already carrying real work here for
 * live updates and rate limiting. A cache belongs in front of something slow,
 * not in front of everything.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }
}
