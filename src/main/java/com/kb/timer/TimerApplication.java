package com.kb.timer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 카카오뱅크 타이머 알림 서비스 메인 애플리케이션
 * 
 * 주요 기능:
 * - 실시간 타이머 공유
 * - WebSocket 기반 실시간 통신
 * - Kafka 기반 분산 메시징
 * - Redis 기반 연결 상태 관리
 * - MongoDB 기반 타임스탬프 저장
 */
@SpringBootApplication
@EnableScheduling
public class TimerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TimerApplication.class, args);
    }
}
