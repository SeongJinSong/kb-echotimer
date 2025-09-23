package com.kb.timer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 * Reactive Redis 템플릿 및 직렬화 설정
 */
@Configuration
public class RedisConfig {
    
    /**
     * Reactive Redis Template 설정
     * 연결 상태 관리용 Redis 템플릿
     */
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        // ObjectMapper 설정 (Java 8 Time API 지원)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // JSON 직렬화 설정
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        
        // 직렬화 컨텍스트 설정
        RedisSerializationContext<String, Object> context = RedisSerializationContext
            .<String, Object>newSerializationContext(new StringRedisSerializer())
            .value(jsonSerializer)
            .hashKey(new StringRedisSerializer())
            .hashValue(jsonSerializer)
            .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
    
    /**
     * String 전용 Reactive Redis Template
     * 간단한 문자열 저장용
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        RedisSerializationContext<String, String> context = RedisSerializationContext
            .<String, String>newSerializationContext(new StringRedisSerializer())
            .value(new StringRedisSerializer())
            .hashKey(new StringRedisSerializer())
            .hashValue(new StringRedisSerializer())
            .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
