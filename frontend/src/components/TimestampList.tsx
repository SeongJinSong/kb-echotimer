import React, { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  List,
  ListItem,
  Chip,
  Divider,
  CircularProgress,
  Alert,
  Stack
} from '@mui/material';
import {
  AccessTime,
  Person,
  History
} from '@mui/icons-material';
import { TimestampEntry } from '../types/timer';
import { TimerApiService } from '../services/api';

/**
 * 타임스탬프 목록 컴포넌트
 * 저장된 타임스탬프들을 시간순으로 표시
 */

interface TimestampListProps {
  /** 타이머 ID */
  timerId: string;
  /** 현재 사용자 ID */
  userId: string;
  /** 새로운 타임스탬프가 추가되었을 때 리프레시 트리거 */
  refreshTrigger?: number;
}

export const TimestampList: React.FC<TimestampListProps> = ({
  timerId,
  userId,
  refreshTrigger = 0
}) => {
  const [timestamps, setTimestamps] = useState<TimestampEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * 타임스탬프 목록 로드 (사용자별)
   */
  const loadTimestamps = async () => {
    if (!timerId || !userId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      // 먼저 사용자별 API 시도, 실패하면 전체 목록에서 필터링
      let timestampList;
      try {
        timestampList = await TimerApiService.getUserTimerHistory(timerId, userId);
      } catch (userApiError) {
        console.warn('사용자별 API 실패, 전체 목록에서 필터링:', userApiError);
        const allTimestamps = await TimerApiService.getTimerHistory(timerId);
        timestampList = allTimestamps.filter(ts => ts.userId === userId);
      }
      setTimestamps(timestampList);
    } catch (err) {
      console.error('타임스탬프 목록 로드 실패:', err);
      setError('타임스탬프 목록을 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 컴포넌트 마운트 시 및 refreshTrigger 변경 시 데이터 로드
   */
  useEffect(() => {
    loadTimestamps();
  }, [timerId, userId, refreshTrigger]);

  /**
   * 시간을 MM:SS 형식으로 포맷팅
   */
  const formatDuration = (milliseconds: number): string => {
    if (!milliseconds || isNaN(milliseconds) || milliseconds < 0) {
      return '00:00';
    }
    const totalSeconds = Math.floor(milliseconds / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  /**
   * 저장 시점 기준으로 실제 남은 시간을 계산
   * (목표 시각이 변경되어도 정확한 남은 시간 표시)
   */
  const calculateActualRemainingTime = (timestamp: TimestampEntry): number => {
    const savedAt = new Date(timestamp.createdAt).getTime();
    const targetTime = new Date(timestamp.targetTime).getTime();
    const remainingTime = targetTime - savedAt;
    return Math.max(0, remainingTime);
  };

  /**
   * 날짜를 한국어 형식으로 포맷팅
   */
  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  /**
   * 사용자 ID를 짧은 형태로 표시
   */
  const formatUserId = (userId: string): string => {
    if (userId.length <= 8) return userId;
    return `${userId.substring(0, 4)}...${userId.substring(userId.length - 4)}`;
  };

  if (loading) {
    return (
      <Card elevation={2} sx={{ mt: 2 }}>
        <CardContent>
          <Box display="flex" justifyContent="center" alignItems="center" py={3}>
            <CircularProgress size={24} />
            <Typography variant="body2" color="text.secondary" ml={2}>
              타임스탬프 목록을 불러오는 중...
            </Typography>
          </Box>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card elevation={2} sx={{ mt: 2 }}>
        <CardContent>
          <Alert severity="error">{error}</Alert>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card elevation={2} sx={{ mt: 2 }}>
      <CardContent>
        {/* 헤더 */}
        <Stack direction="row" alignItems="center" spacing={1} mb={2}>
          <History color="primary" />
          <Typography variant="h6" color="primary">
            저장된 시점들
          </Typography>
          <Chip 
            label={`${timestamps.length}개`} 
            size="small" 
            color="primary" 
            variant="outlined" 
          />
        </Stack>

        {timestamps.length === 0 ? (
          <Box textAlign="center" py={3}>
            <Typography variant="body2" color="text.secondary">
              아직 저장된 시점이 없습니다.
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" mt={1}>
              타이머 진행 중 '저장' 버튼을 눌러 현재 시점을 기록해보세요.
            </Typography>
          </Box>
        ) : (
          <List disablePadding>
            {timestamps.map((timestamp, index) => (
              <React.Fragment key={timestamp.id || index}>
                <ListItem
                  sx={{
                    px: 0,
                    py: 1.5,
                    '&:hover': {
                      backgroundColor: 'rgba(0, 0, 0, 0.04)'
                    },
                    flexDirection: 'column',
                    alignItems: 'stretch'
                  }}
                >
                  {/* Primary content */}
                  <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
                    <AccessTime fontSize="small" color="action" />
                    <Typography variant="body1" fontWeight="medium">
                      버튼 누른 시점: {formatDate(timestamp.createdAt)}
                    </Typography>
                    <Chip
                      icon={<Person />}
                      label={formatUserId(timestamp.userId)}
                      size="small"
                      variant="outlined"
                      sx={{ ml: 'auto' }}
                    />
                  </Stack>

                  {/* Secondary content */}
                  <Stack spacing={0.5}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="caption" color="text.secondary">
                        📅 현재 시각: {formatDate(timestamp.createdAt)}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        ⏰ 남은 시간: {formatDuration(calculateActualRemainingTime(timestamp))}
                      </Typography>
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      🎯 목표 시간: {new Date(timestamp.targetTime).toLocaleString('ko-KR', {
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit'
                      })}
                    </Typography>
                  </Stack>
                </ListItem>
                {index < timestamps.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List>
        )}
      </CardContent>
    </Card>
  );
};
