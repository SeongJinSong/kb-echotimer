package com.kb.timer.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * 목표 시간 변경 요청 DTO
 */
@Data
public class ChangeTargetTimeRequest {
    
    /**
     * 새로운 목표 시간
     */
    @NotNull(message = "새로운 목표 시간은 필수입니다")
    private Instant newTargetTime;
    
    /**
     * 변경 요청자 ID
     */
    @NotBlank(message = "변경 요청자 ID는 필수입니다")
    private String changedBy;
}
