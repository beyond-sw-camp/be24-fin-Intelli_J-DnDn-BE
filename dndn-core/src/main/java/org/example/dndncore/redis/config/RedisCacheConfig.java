package org.example.dndncore.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dndncore.redis.cache.RedisCacheNames;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    private static final Duration ESG_DASHBOARD_TTL = Duration.ofMinutes(5);

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper
    ) {
        GenericJackson2JsonRedisSerializer valueSerializer =
                GenericJackson2JsonRedisSerializer.builder()
                        .objectMapper(objectMapper.copy())
                        .defaultTyping(true)
                        .typeHintPropertyName("@class")
                        .build();

        RedisCacheConfiguration baseConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(baseConfiguration)
                .withCacheConfiguration(
                        RedisCacheNames.ESG_DASHBOARD,
                        baseConfiguration.entryTtl(ESG_DASHBOARD_TTL)
                )
                .transactionAware()
                .build();
    }
}
