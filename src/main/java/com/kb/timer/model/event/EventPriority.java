package com.kb.timer.model.event;

/**
 * 이벤트 우선순위
 * 금융 서비스 특성상 중요도에 따른 차별화 처리
 */
public enum EventPriority {
    /**
     * 중요 이벤트 - 절대 유실 불가
     * 예: 타이머 완료, 보안 관련 이벤트
     */
    CRITICAL,
    
    /**
     * 중간 우선순위 이벤트
     * 예: 목표 시간 변경, 타임스탬프 저장
     */
    IMPORTANT,
    
    /**
     * 일반 이벤트
     * 예: 사용자 입장/퇴장
     */
    NORMAL
}
