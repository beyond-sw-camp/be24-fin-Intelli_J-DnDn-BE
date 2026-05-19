package org.example.dndncore.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                .setMasterName(redisProperties.getSentinel().getMaster());

        redisProperties.getSentinel().getNodes().forEach(node ->
                sentinelServersConfig.addSentinelAddress("redis://" + node)
        );

        if (redisProperties.getPassword() != null && StringUtils.hasText(redisProperties.getPassword().toString())) {
            sentinelServersConfig.setPassword(redisProperties.getPassword().toString());
        }

        return Redisson.create(config);
    }
}
