package com.kb.timer.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서버 인스턴스 ID 생성기
 * 분산 환경에서 각 서버를 고유하게 식별하기 위한 ID 생성
 */
@Component
public class ServerInstanceIdGenerator {

    @Value("${server.instance.id}")
    private String serverInstanceId;

    /**
     * 현재 서버의 고유 인스턴스 ID를 반환합니다.
     * 
     * @return 서버 인스턴스 ID
     */
    public String getServerInstanceId() {
        return serverInstanceId;
    }
}
