package com.kb.timer.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * 타임스탬프 저장 요청 DTO
 */
@Data
public class SaveTimestampRequest {
    
    /**
     * 사용자 ID
     */
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;
    
    /**
     * 목표 시간
     */
    @NotNull(message = "목표 시간은 필수입니다")
    private Instant targetTime;
    
    /**
     * 추가 메타데이터
     */
    private Map<String, Object> metadata;
}
