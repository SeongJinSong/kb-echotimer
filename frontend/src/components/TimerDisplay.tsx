import React from 'react';
import {
  Box,
  Typography,
  LinearProgress,
  Chip,
  Card,
  CardContent,
  Stack,
  IconButton,
  Tooltip
} from '@mui/material';
import {
  Stop,
  Save,
  Share,
  People,
  Edit
} from '@mui/icons-material';

/**
 * 타이머 디스플레이 컴포넌트
 * 타이머의 현재 상태와 진행률을 시각적으로 표시
 */

interface TimerDisplayProps {
  /** 남은 시간 (초) */
  remainingSeconds: number;
  /** 완료 여부 */
  isCompleted: boolean;
  /** 진행률 (0-100) */
  progress: number;
  /** 온라인 사용자 수 */
  onlineUserCount?: number;
  /** 사용자 역할 */
  userRole?: 'OWNER' | 'VIEWER';
  /** 타이머 ID (공유용) */
  timerId?: string;
  
  /** 액션 핸들러들 */
  onSave?: () => void;
  onShare?: () => void;
  onComplete?: () => void;
  onEditTargetTime?: () => void;
}

export const TimerDisplay: React.FC<TimerDisplayProps> = ({
  remainingSeconds,
  isCompleted,
  progress,
  onlineUserCount = 0,
  userRole = 'VIEWER',
  timerId,
  onSave,
  onShare,
  onComplete,
  onEditTargetTime
}) => {
  
  /**
   * 시간을 MM:SS 형식으로 포맷팅
   */
  const formatTime = (seconds: number): string => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
  };

  /**
   * 남은 시간에 따른 색상 결정
   */
  const getProgressColor = (): 'primary' | 'warning' | 'error' => {
    if (isCompleted) return 'primary'; // 완료된 경우 파란색
    if (remainingSeconds > 300) return 'primary'; // 5분 이상 남은 경우 파란색
    if (remainingSeconds > 60) return 'warning'; // 1분 이상 남은 경우 주황색
    return 'error'; // 1분 미만 남은 경우 빨간색
  };

  /**
   * 상태에 따른 메시지
   */
  const getStatusMessage = (): string => {
    if (isCompleted) return '🎉 타이머 완료!';
    if (remainingSeconds < 60) return '⏰ 1분 미만 남음';
    if (remainingSeconds < 300) return '⚡ 5분 미만 남음';
    return '⏱️ 진행 중';
  };

  return (
    <Card 
      elevation={3}
      sx={{ 
        maxWidth: 500, 
        mx: 'auto',
        background: isCompleted 
          ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
          : 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
      }}
    >
      <CardContent sx={{ p: 3 }}>
        {/* 헤더 영역 */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6" color="white" fontWeight="bold">
            {getStatusMessage()}
          </Typography>
          
          {/* 온라인 사용자 수 */}
          <Chip
            icon={<People />}
            label={`${onlineUserCount}명 접속`}
            size="small"
            sx={{ 
              backgroundColor: 'rgba(255,255,255,0.2)',
              color: 'white',
              '& .MuiChip-icon': { color: 'white' }
            }}
          />
        </Stack>

        {/* 메인 타이머 디스플레이 */}
        <Box textAlign="center" mb={3}>
          <Typography 
            variant="h1" 
            component="div"
            sx={{ 
              fontSize: { xs: '3rem', sm: '4rem' },
              fontWeight: 'bold',
              color: 'white',
              textShadow: '2px 2px 4px rgba(0,0,0,0.3)',
              fontFamily: 'monospace'
            }}
          >
            {formatTime(remainingSeconds)}
          </Typography>
          
          {/* 전체 시간 표시 제거 - 의미가 없음 */}
        </Box>

        {/* 진행률 바 */}
        <Box mb={3}>
          <LinearProgress
            variant="determinate"
            value={progress}
            color={getProgressColor()}
            sx={{
              height: 12,
              borderRadius: 6,
              backgroundColor: 'rgba(255,255,255,0.2)',
              '& .MuiLinearProgress-bar': {
                borderRadius: 6,
              }
            }}
          />
          {/* 진행률 표시 제거 - NaN 문제 */}
        </Box>

        {/* 액션 버튼들 */}
        <Stack direction="row" justifyContent="center" spacing={1}>
          {/* 타임스탬프 저장 */}
          <Tooltip title="현재 시점 저장">
            <IconButton
              onClick={onSave}
              disabled={isCompleted}
              sx={{ 
                backgroundColor: 'rgba(255,255,255,0.1)',
                color: 'white',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.2)' },
                '&:disabled': { opacity: 0.5 }
              }}
            >
              <Save />
            </IconButton>
          </Tooltip>

          {/* 공유 */}
          <Tooltip title="타이머 공유">
            <IconButton
              onClick={onShare}
              sx={{ 
                backgroundColor: 'rgba(255,255,255,0.1)',
                color: 'white',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.2)' }
              }}
            >
              <Share />
            </IconButton>
          </Tooltip>

          {/* 정지 (Owner만) */}
          {userRole === 'OWNER' && (
            <Tooltip title="타이머 정지">
              <IconButton
                onClick={onComplete}
                disabled={isCompleted}
                sx={{ 
                  backgroundColor: 'rgba(255,255,255,0.1)',
                  color: 'white',
                  '&:hover': { backgroundColor: 'rgba(255,255,255,0.2)' },
                  '&:disabled': { opacity: 0.5 }
                }}
              >
                <Stop />
              </IconButton>
            </Tooltip>
          )}

          {/* 기준 시각 수정 (Owner만, 완료되지 않았을 때만) */}
          {userRole === 'OWNER' && !isCompleted && onEditTargetTime && (
            <Tooltip title="기준 시각 수정">
              <IconButton
                onClick={() => {
                  console.log('🔧 기준 시각 수정 버튼 클릭 - userRole:', userRole, 'isCompleted:', isCompleted);
                  onEditTargetTime();
                }}
                sx={{ 
                  backgroundColor: 'rgba(255,255,255,0.1)',
                  color: 'white',
                  '&:hover': { backgroundColor: 'rgba(255,255,255,0.2)' }
                }}
              >
                <Edit />
              </IconButton>
            </Tooltip>
          )}
        </Stack>

        {/* 타이머 ID (개발용) */}
        {timerId && (
          <Typography 
            variant="caption" 
            color="rgba(255,255,255,0.6)" 
            display="block" 
            textAlign="center" 
            mt={2}
            sx={{ fontFamily: 'monospace' }}
          >
            Timer ID: {timerId}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
};
