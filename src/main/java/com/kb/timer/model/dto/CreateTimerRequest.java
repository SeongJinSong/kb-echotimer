package com.kb.timer.model.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 타이머 생성 요청 DTO
 */
@Data
public class CreateTimerRequest {
    
    /**
     * 목표 시간 (초)
     */
    @Min(value = 1, message = "목표 시간은 1초 이상이어야 합니다")
    private long targetTimeSeconds;
    
    /**
     * 타이머 소유자 ID
     */
    @NotBlank(message = "소유자 ID는 필수입니다")
    private String ownerId;
}
