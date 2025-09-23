import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Stack,
  Slider,
  Chip,
  Alert
} from '@mui/material';
import { Timer, Add } from '@mui/icons-material';

/**
 * 타이머 생성 컴포넌트
 * 새로운 타이머를 생성하기 위한 UI 제공
 */

interface TimerCreatorProps {
  /** 로딩 상태 */
  loading?: boolean;
  /** 에러 메시지 */
  error?: string | null;
  /** 타이머 생성 핸들러 */
  onCreateTimer: (targetTimeSeconds: number) => void;
}

export const TimerCreator: React.FC<TimerCreatorProps> = ({
  loading = false,
  error,
  onCreateTimer
}) => {
  const [minutes, setMinutes] = useState(25); // 기본값: 25분 (포모도로)
  const [customTime, setCustomTime] = useState('');
  const [useCustomTime, setUseCustomTime] = useState(false);
  const [targetDateTime, setTargetDateTime] = useState(() => {
    // 초기값: 현재 시각에서 3분 후 (로컬 시간대 적용)
    const now = new Date();
    now.setMinutes(now.getMinutes() + 3);
    
    // 로컬 시간대를 고려한 ISO 문자열 생성
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  });
  const [useTargetTime, setUseTargetTime] = useState(true); // 기본값을 목표 시각 모드로 변경

  /**
   * 미리 정의된 시간 옵션들
   */
  const presetTimes = [
    { label: '5분', minutes: 5 },
    { label: '10분', minutes: 10 },
    { label: '15분', minutes: 15 },
    { label: '25분 (포모도로)', minutes: 25 },
    { label: '30분', minutes: 30 },
    { label: '45분', minutes: 45 },
    { label: '1시간', minutes: 60 },
    { label: '2시간', minutes: 120 }
  ];

  /**
   * 시간을 분:초 형식으로 포맷팅
   */
  const formatTime = (totalMinutes: number): string => {
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    
    if (hours > 0) {
      return `${hours}시간 ${minutes}분`;
    }
    return `${minutes}분`;
  };

  /**
   * 커스텀 시간 파싱 (MM:SS 또는 MM 형식)
   */
  const parseCustomTime = (timeString: string): number => {
    const trimmed = timeString.trim();
    
    // MM:SS 형식
    if (trimmed.includes(':')) {
      const [minutesPart, secondsPart] = trimmed.split(':');
      const minutes = parseInt(minutesPart) || 0;
      const seconds = parseInt(secondsPart) || 0;
      return minutes * 60 + seconds;
    }
    
    // MM 형식 (분만)
    const minutes = parseInt(trimmed) || 0;
    return minutes * 60;
  };

  /**
   * 타이머 생성 핸들러
   */
  const handleCreateTimer = () => {
    let targetTimeSeconds: number;
    
    if (useTargetTime && targetDateTime) {
      // 목표 시각 모드: 현재 시간부터 목표 시각까지의 차이 계산
      const targetTime = new Date(targetDateTime);
      const now = new Date();
      const diffMs = targetTime.getTime() - now.getTime();
      
      if (diffMs <= 0) {
        alert('목표 시각은 현재 시간보다 미래여야 합니다.');
        return;
      }
      
      targetTimeSeconds = Math.floor(diffMs / 1000);
    } else if (useCustomTime && customTime) {
      targetTimeSeconds = parseCustomTime(customTime);
    } else {
      targetTimeSeconds = minutes * 60;
    }
    
    if (targetTimeSeconds <= 0) {
      alert('올바른 시간을 입력해주세요.');
      return;
    }
    
    onCreateTimer(targetTimeSeconds);
  };

  /**
   * 프리셋 시간 선택 핸들러
   */
  const handlePresetSelect = (presetMinutes: number) => {
    // 현재 시각에서 선택한 시간만큼 더한 목표 시각 설정 (로컬 시간대 적용)
    const now = new Date();
    now.setMinutes(now.getMinutes() + presetMinutes);
    
    // 로컬 시간대를 고려한 ISO 문자열 생성
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    
    setTargetDateTime(`${year}-${month}-${day}T${hours}:${minutes}`);
    setMinutes(presetMinutes);
    setUseCustomTime(false);
    setUseTargetTime(true); // 목표 시각 모드 유지
    setCustomTime('');
  };

  /**
   * 커스텀 시간 입력 활성화
   */
  const handleCustomTimeToggle = () => {
    setUseCustomTime(!useCustomTime);
    setUseTargetTime(false);
    if (!useCustomTime) {
      setCustomTime('');
    }
    // 목표 시각 초기화 (로컬 시간대 적용)
    const now = new Date();
    now.setMinutes(now.getMinutes() + 3);
    
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    
    setTargetDateTime(`${year}-${month}-${day}T${hours}:${minutes}`);
  };

  /**
   * 목표 시각 입력 활성화
   */
  const handleTargetTimeToggle = () => {
    setUseTargetTime(!useTargetTime);
    setUseCustomTime(false);
    if (!useTargetTime) {
      // 기본값: 현재 시간 + 3분 (로컬 시간대 적용)
      const defaultTarget = new Date();
      defaultTarget.setMinutes(defaultTarget.getMinutes() + 3);
      
      const year = defaultTarget.getFullYear();
      const month = String(defaultTarget.getMonth() + 1).padStart(2, '0');
      const day = String(defaultTarget.getDate()).padStart(2, '0');
      const hours = String(defaultTarget.getHours()).padStart(2, '0');
      const minutes = String(defaultTarget.getMinutes()).padStart(2, '0');
      
      setTargetDateTime(`${year}-${month}-${day}T${hours}:${minutes}`);
    }
  };

  return (
    <Card elevation={2} sx={{ maxWidth: 500, mx: 'auto' }}>
      <CardContent sx={{ p: 3 }}>
        {/* 헤더 */}
        <Stack direction="row" alignItems="center" spacing={1} mb={3}>
          <Timer color="primary" />
          <Typography variant="h5" fontWeight="bold">
            새 타이머 만들기
          </Typography>
        </Stack>

        {/* 에러 메시지 */}
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {/* 프리셋 시간 선택 */}
        <Typography variant="subtitle1" gutterBottom fontWeight="medium">
          빠른 선택
        </Typography>
        <Box mb={3}>
          <Stack direction="row" flexWrap="wrap" gap={1}>
            {presetTimes.map((preset) => (
              <Chip
                key={preset.minutes}
                label={preset.label}
                onClick={() => handlePresetSelect(preset.minutes)}
                color={minutes === preset.minutes && !useCustomTime && !useTargetTime ? 'primary' : 'default'}
                variant={minutes === preset.minutes && !useCustomTime && !useTargetTime ? 'filled' : 'outlined'}
                sx={{ cursor: 'pointer' }}
              />
            ))}
          </Stack>
        </Box>

        {/* 슬라이더로 시간 조정 */}
        {!useCustomTime && !useTargetTime && (
          <Box mb={3}>
            <Typography variant="subtitle1" gutterBottom fontWeight="medium">
              시간 조정: {formatTime(minutes)}
            </Typography>
            <Slider
              value={minutes}
              onChange={(_, value) => setMinutes(value as number)}
              min={1}
              max={180} // 3시간
              step={1}
              marks={[
                { value: 5, label: '5분' },
                { value: 25, label: '25분' },
                { value: 60, label: '1시간' },
                { value: 120, label: '2시간' }
              ]}
              valueLabelDisplay="auto"
              valueLabelFormat={formatTime}
            />
          </Box>
        )}

        {/* 입력 방식 선택 */}
        <Box mb={3}>
          <Stack direction="row" spacing={1} mb={2}>
            <Button
              variant={useTargetTime ? "contained" : "outlined"}
              onClick={handleTargetTimeToggle}
            >
              목표 시각
            </Button>
            <Button
              variant={!useCustomTime && !useTargetTime ? "contained" : "outlined"}
              onClick={() => {
                setUseCustomTime(false);
                setUseTargetTime(false);
              }}
            >
              프리셋
            </Button>
            <Button
              variant={useCustomTime ? "contained" : "outlined"}
              onClick={handleCustomTimeToggle}
            >
              직접 입력
            </Button>
          </Stack>
          
          {/* 커스텀 시간 입력 */}
          {useCustomTime && (
            <TextField
              fullWidth
              label="시간 입력"
              placeholder="예: 25 (25분) 또는 25:30 (25분 30초)"
              value={customTime}
              onChange={(e) => setCustomTime(e.target.value)}
              helperText="분 단위 또는 분:초 형식으로 입력하세요"
              variant="outlined"
            />
          )}

          {/* 목표 시각 입력 */}
          {useTargetTime && (
            <TextField
              fullWidth
              label="목표 시각"
              type="datetime-local"
              value={targetDateTime}
              onChange={(e) => setTargetDateTime(e.target.value)}
              helperText="타이머가 완료될 목표 시각을 설정하세요"
              variant="outlined"
              InputLabelProps={{
                shrink: true,
              }}
            />
          )}
        </Box>

        {/* 미리보기 */}
        <Box 
          sx={{ 
            p: 2, 
            backgroundColor: 'grey.50', 
            borderRadius: 1, 
            mb: 3,
            textAlign: 'center'
          }}
        >
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {useTargetTime ? '목표 시각' : '설정된 시간'}
          </Typography>
          <Typography variant="h4" color="primary" fontWeight="bold">
            {useTargetTime && targetDateTime ? (
              new Date(targetDateTime).toLocaleString('ko-KR')
            ) : useCustomTime && customTime ? (
              formatTime(Math.floor(parseCustomTime(customTime) / 60))
            ) : (
              formatTime(minutes)
            )}
          </Typography>
          {useTargetTime && targetDateTime && (
            <Typography variant="body2" color="text.secondary" mt={1}>
              약 {Math.floor((new Date(targetDateTime).getTime() - new Date().getTime()) / 60000)}분 후
            </Typography>
          )}
        </Box>

        {/* 생성 버튼 */}
        <Button
          fullWidth
          variant="contained"
          size="large"
          onClick={handleCreateTimer}
          disabled={
            loading || 
            (useCustomTime && !customTime) || 
            (useTargetTime && !targetDateTime)
          }
          startIcon={<Add />}
          sx={{ py: 1.5 }}
        >
          {loading ? '타이머 생성 중...' : '타이머 시작'}
        </Button>

        {/* 도움말 */}
        <Typography 
          variant="caption" 
          color="text.secondary" 
          display="block" 
          textAlign="center" 
          mt={2}
        >
          💡 타이머가 생성되면 다른 사람들과 실시간으로 공유할 수 있습니다
        </Typography>
      </CardContent>
    </Card>
  );
};
