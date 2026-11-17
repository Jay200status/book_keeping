package com.example.bookkeeping.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisScriptConfig {
    /**
     * 释放redis分布式锁的lus脚本的bean
     * @return
     */
    @Bean
    public DefaultRedisScript<Long> unlockScript(){
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        /*指定lua脚本路径*/
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/unlock.lua")));
        /*设置返回类型为long*/
        redisScript.setResultType(Long.class);

        return  redisScript;
    }
}
