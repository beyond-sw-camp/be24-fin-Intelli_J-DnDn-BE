package org.example.dndncore.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            RedisProperties redisProperties,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean redisSslEnabled
    ) {
        Config config = new Config();
        String scheme = redisSslEnabled ? "rediss://" : "redis://";

        if (hasSentinelConfig(redisProperties)) {
            SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                    .setMasterName(redisProperties.getSentinel().getMaster());

            redisProperties.getSentinel().getNodes().forEach(node ->
                    sentinelServersConfig.addSentinelAddress(scheme + node)
            );

            if (hasPassword(redisProperties)) {
                sentinelServersConfig.setPassword(redisProperties.getPassword().toString());
            }
        } else {
            SingleServerConfig singleServerConfig = config.useSingleServer()
                    .setAddress(scheme + redisProperties.getHost() + ":" + redisProperties.getPort());

            if (hasPassword(redisProperties)) {
                singleServerConfig.setPassword(redisProperties.getPassword().toString());
            }
        }

        return Redisson.create(config);
    }

    private boolean hasSentinelConfig(RedisProperties redisProperties) {
        return redisProperties.getSentinel() != null
                && StringUtils.hasText(redisProperties.getSentinel().getMaster())
                && redisProperties.getSentinel().getNodes() != null
                && !redisProperties.getSentinel().getNodes().isEmpty();
    }

    private boolean hasPassword(RedisProperties redisProperties) {
        return redisProperties.getPassword() != null
                && StringUtils.hasText(redisProperties.getPassword().toString());
    }
}
