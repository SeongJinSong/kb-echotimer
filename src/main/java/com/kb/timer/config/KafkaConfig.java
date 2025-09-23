package com.kb.timer.config;

import com.kb.timer.model.event.TimerEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정 클래스
 * Reactive Kafka Producer/Consumer 설정
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${server.instance.id:unknown}")
    private String serverInstanceId;
    
    /**
     * Reactive Kafka Producer 설정
     */
    @Bean
    public SenderOptions<String, Object> kafkaProducerOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 성능 최적화 설정
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // 리더만 확인
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // 클라이언트 ID 설정 (디버깅용)
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "timer-producer-" + getServerInstanceId());
        
        return SenderOptions.create(props);
    }
    
    /**
     * Reactive Kafka Consumer 설정
     */
    @Bean
    public ReceiverOptions<String, TimerEvent> kafkaConsumerOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "timer-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // JSON 역직렬화 설정
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.kb.timer.model.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TimerEvent.class.getName());
        
        // 성능 최적화 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        // 클라이언트 ID 설정 (디버깅용)
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "timer-consumer-" + getServerInstanceId());
        
        return ReceiverOptions.create(props);
    }
    
    /**
     * 서버 인스턴스 ID 생성
     * 호스트명 + 프로세스 ID 조합으로 고유 식별자 생성
     */
    private String getServerInstanceId() {
        if (!"unknown".equals(serverInstanceId)) {
            return serverInstanceId;
        }
        
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String processId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            String generatedId = hostname + "-" + processId;
            
            log.info("Generated server instance ID: {}", generatedId);
            return generatedId;
        } catch (UnknownHostException e) {
            String fallbackId = "server-" + System.currentTimeMillis();
            log.warn("Failed to generate server instance ID, using fallback: {}", fallbackId);
            return fallbackId;
        }
    }
}
