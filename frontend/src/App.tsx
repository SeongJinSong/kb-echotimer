import { useState, useEffect, useCallback } from 'react';
import {
  ThemeProvider,
  createTheme,
  CssBaseline,
  Container,
  Typography,
  Box,
  Fab,
  Snackbar,
  Alert,
  Chip,
  Stack,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField
} from '@mui/material';
import { Add, Wifi, WifiOff } from '@mui/icons-material';

import { TimerDisplay } from './components/TimerDisplay';
import { TimerCreator } from './components/TimerCreator';
import { TimestampList } from './components/TimestampList';
import { useTimer } from './hooks/useTimer';
import { TimerApiService } from './services/api';

/**
 * 메인 애플리케이션 컴포넌트
 * 타이머 생성과 관리를 위한 전체 UI 구성
 */

// Material-UI 테마 설정
const theme = createTheme({
  palette: {
    primary: {
      main: '#667eea',
    },
    secondary: {
      main: '#f093fb',
    },
    background: {
      default: '#f5f7fa',
    },
  },
  typography: {
    fontFamily: '"Noto Sans KR", "Roboto", "Helvetica", "Arial", sans-serif',
  },
  shape: {
    borderRadius: 12,
  },
});

function App() {
  // 상태 관리
  const [showCreator, setShowCreator] = useState(true);
  const [userId] = useState(() => `user-${Date.now()}`); // 임시 사용자 ID 생성
  const [currentTimerId, setCurrentTimerId] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'info' | 'warning';
  }>({
    open: false,
    message: '',
    severity: 'info'
  });
  const [timestampRefreshTrigger, setTimestampRefreshTrigger] = useState(0);
  const [showTargetTimeDialog, setShowTargetTimeDialog] = useState(false);
  const [newTargetTime, setNewTargetTime] = useState('');
  // 공유 타이머 여부는 이제 WebSocket 이벤트로 처리됨

  // 브라우저 알림 권한 요청
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission().then(permission => {
        console.log('알림 권한:', permission);
      });
    }
  }, []);

  // URL에서 타이머 ID 추출 (공유 링크 지원)
  useEffect(() => {
    const path = window.location.pathname;
    const urlParams = new URLSearchParams(window.location.search);
    
    // /timer/{shareToken} 형식의 URL 처리
    const timerMatch = path.match(/^\/timer\/(.+)$/);
    if (timerMatch) {
      const shareToken = timerMatch[1];
      setCurrentTimerId(shareToken);
      setShowCreator(false);
      return;
    }
    
    // 쿼리 파라미터 방식 (기존 호환성)
    const timerIdFromUrl = urlParams.get('timer');
    if (timerIdFromUrl) {
      setCurrentTimerId(timerIdFromUrl);
      setShowCreator(false);
    }
  }, []);

  /**
   * 타이머 완료 알림 핸들러
   */
  const handleTimerCompleted = useCallback(() => {
    showSnackbar('🎉 타이머가 완료되었습니다!', 'success');
    
    // 브라우저 알림 (권한이 있는 경우)
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('KB EchoTimer', {
        body: '타이머가 완료되었습니다! 🎉',
        icon: '/favicon.svg'
      });
    }
  }, []);

  /**
   * 공유 타이머 접속 알림 핸들러 (소유자에게만 표시)
   */
  const handleSharedTimerAccessed = useCallback((accessedUserId: string) => {
    showSnackbar(`${accessedUserId}님이 공유 타이머에 접속했습니다! 👋`, 'info');
    
    // 브라우저 알림 (권한이 있는 경우)
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('KB EchoTimer', {
        body: `${accessedUserId}님이 공유 타이머에 접속했습니다!`,
        icon: '/favicon.svg'
      });
    }
  }, []);

  // 타이머 훅 사용
  const {
    timer,
    loading,
    error,
    connected,
    createTimer,
    loadTimer,
    saveTimestamp,
    completeTimer,
    remainingSeconds,
    isCompleted,
    progress
  } = useTimer({
    timerId: currentTimerId || undefined,
    userId,
    autoConnect: true,
    isShareToken: !!(currentTimerId && window.location.pathname.startsWith('/timer/')), // 공유 토큰 여부 명시
    onTimerCompleted: handleTimerCompleted, // 타이머 완료 콜백 추가
    onSharedTimerAccessed: handleSharedTimerAccessed // 공유 타이머 접속 콜백 추가
  });

  // 공유 타이머 접속 알림은 이제 WebSocket 이벤트로 처리됨 (소유자에게만 표시)

  /**
   * 스낵바 표시 헬퍼
   */
  const showSnackbar = (
    message: string, 
    severity: 'success' | 'error' | 'info' | 'warning' = 'info'
  ) => {
    setSnackbar({ open: true, message, severity });
  };

  /**
   * 타이머 생성 핸들러
   */
  const handleCreateTimer = async (targetTimeSeconds: number) => {
    try {
      await createTimer(targetTimeSeconds);
      setShowCreator(false);
      showSnackbar('타이머가 생성되었습니다!', 'success');
      
      // URL 업데이트 (공유 가능하도록)
      if (timer?.shareToken) {
        const token = timer.shareToken.replace('/timer/', '');
        const newUrl = `${window.location.origin}/timer/${token}`;
        window.history.pushState({}, '', newUrl);
      }
    } catch (err) {
      showSnackbar('타이머 생성에 실패했습니다.', 'error');
    }
  };

  /**
   * 타임스탬프 저장 핸들러
   */
  const handleSaveTimestamp = async () => {
    try {
      await saveTimestamp();
      showSnackbar('타임스탬프가 저장되었습니다!', 'success');
      // 타임스탬프 목록 리프레시 트리거
      setTimestampRefreshTrigger(prev => prev + 1);
    } catch (err) {
      showSnackbar('타임스탬프 저장에 실패했습니다.', 'error');
    }
  };

  /**
   * 공유 핸들러
   */
  const handleShare = async () => {
    if (!timer?.shareToken) return;
    
    // shareToken에서 /timer/ 부분 제거하고 실제 토큰만 추출
    const token = timer.shareToken.replace('/timer/', '');
    const shareUrl = `${window.location.origin}/timer/${token}`;
    
    try {
      // 클립보드 API 사용
      await navigator.clipboard.writeText(shareUrl);
      showSnackbar('공유 링크가 클립보드에 복사되었습니다!', 'success');
    } catch (err) {
      // 폴백: 텍스트 선택
      const textArea = document.createElement('textarea');
      textArea.value = shareUrl;
      document.body.appendChild(textArea);
      textArea.select();
      document.execCommand('copy');
      document.body.removeChild(textArea);
      showSnackbar('공유 링크가 복사되었습니다!', 'success');
    }
  };

  /**
   * 타이머 완료 핸들러
   */
  const handleCompleteTimer = async () => {
    try {
      await completeTimer();
      showSnackbar('타이머가 완료되었습니다!', 'success');
    } catch (err) {
      showSnackbar('타이머 완료 처리에 실패했습니다.', 'error');
    }
  };

  /**
   * 새 타이머 만들기
   */
  const handleNewTimer = () => {
    setShowCreator(true);
    setCurrentTimerId(null);
    window.history.pushState({}, '', window.location.origin);
  };

  /**
   * 기준 시각 수정 다이얼로그 열기
   */
  const handleOpenTargetTimeDialog = () => {
    if (timer?.targetTime) {
      // 현재 목표 시간을 로컬 시간대로 변환하여 설정
      const currentTarget = new Date(timer.targetTime);
      const year = currentTarget.getFullYear();
      const month = String(currentTarget.getMonth() + 1).padStart(2, '0');
      const day = String(currentTarget.getDate()).padStart(2, '0');
      const hours = String(currentTarget.getHours()).padStart(2, '0');
      const minutes = String(currentTarget.getMinutes()).padStart(2, '0');
      
      setNewTargetTime(`${year}-${month}-${day}T${hours}:${minutes}`);
    }
    setShowTargetTimeDialog(true);
  };

  /**
   * 기준 시각 수정 처리
   */
  const handleChangeTargetTime = async () => {
    if (!newTargetTime || !timer) return;

    try {
      const targetTime = new Date(newTargetTime);
      const now = new Date();
      
      if (targetTime <= now) {
        showSnackbar('목표 시각은 현재 시간보다 미래여야 합니다.', 'error');
        return;
      }

      // API 호출
      await TimerApiService.changeTargetTime(timer.timerId, {
        newTargetTime: targetTime.toISOString(),
        changedBy: userId
      });

      showSnackbar('기준 시각이 변경되었습니다!', 'success');
      setShowTargetTimeDialog(false);
      setNewTargetTime('');
      
      // 타이머 정보 수동 새로고침
      if (currentTimerId) {
        await loadTimer(currentTimerId);
      } else if (timer.timerId) {
        // currentTimerId가 없으면 timer.timerId로 시도
        await loadTimer(timer.timerId);
      }
      
      // 타임스탬프 목록도 새로고침
      setTimestampRefreshTrigger(prev => prev + 1);
    } catch (err) {
      console.error('기준 시각 변경 실패:', err);
      showSnackbar('기준 시각 변경에 실패했습니다.', 'error');
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      
      {/* 메인 컨텐츠 */}
      <Container maxWidth="md" sx={{ py: 4 }}>
        {/* 헤더 영역 */}
        <Box textAlign="center" mb={4}>
          <Typography variant="h4" component="h1" gutterBottom sx={{ fontWeight: 'bold' }}>
            🕐 KB EchoTimer
          </Typography>
          
          {/* 연결 상태 표시 */}
          <Chip
            icon={connected ? <Wifi /> : <WifiOff />}
            label={connected ? '연결됨' : '연결 안됨'}
            color={connected ? 'success' : 'error'}
            size="small"
            variant="outlined"
          />
        </Box>
        {showCreator ? (
          /* 타이머 생성 화면 */
          <TimerCreator
            loading={loading}
            error={error}
            onCreateTimer={handleCreateTimer}
          />
        ) : timer ? (
          /* 타이머 실행 화면 */
          <Box>
            <TimerDisplay
              remainingSeconds={remainingSeconds}
              isCompleted={isCompleted}
              progress={progress}
              onlineUserCount={timer.onlineUserCount}
              userRole={timer.userRole}
              timerId={timer.timerId}
              onSave={handleSaveTimestamp}
              onShare={handleShare}
              onComplete={handleCompleteTimer}
              onEditTargetTime={handleOpenTargetTimeDialog}
            />
            
            {/* 타이머 정보 */}
            <Box mt={3} textAlign="center">
              <Stack spacing={1} alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  타이머 소유자: {timer.ownerId}
                </Typography>
                <Typography variant="body2" color="primary.main" fontWeight="medium">
                  현재 사용자: {userId}
                </Typography>
              </Stack>
              
              <Typography variant="body2" color="text.secondary" mt={1}>
                목표 시간: {new Date(timer.targetTime).toLocaleString('ko-KR')}
              </Typography>
            </Box>
            
            {/* 타임스탬프 목록 */}
            <TimestampList 
              timerId={timer.timerId} 
              userId={userId}
              refreshTrigger={timestampRefreshTrigger}
            />
          </Box>
        ) : (
          /* 로딩 상태 */
          <Box textAlign="center" py={8}>
            <Typography variant="h6" color="text.secondary">
              타이머를 불러오는 중...
            </Typography>
          </Box>
        )}
      </Container>

      {/* 새 타이머 생성 FAB */}
      {!showCreator && (
        <Fab
          color="primary"
          aria-label="새 타이머"
          onClick={handleNewTimer}
          sx={{
            position: 'fixed',
            bottom: 24,
            right: 24,
          }}
        >
          <Add />
        </Fab>
      )}

      {/* 기준 시각 수정 다이얼로그 */}
      <Dialog
        open={showTargetTimeDialog}
        onClose={() => setShowTargetTimeDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>기준 시각 수정</DialogTitle>
        <DialogContent>
          <TextField
            label="새로운 목표 시각"
            type="datetime-local"
            value={newTargetTime}
            onChange={(e) => setNewTargetTime(e.target.value)}
            fullWidth
            margin="normal"
            InputLabelProps={{
              shrink: true,
            }}
            helperText="목표 시각은 현재 시간보다 미래여야 합니다."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowTargetTimeDialog(false)}>
            취소
          </Button>
          <Button 
            onClick={handleChangeTargetTime}
            variant="contained"
            disabled={!newTargetTime}
          >
            변경
          </Button>
        </DialogActions>
      </Dialog>

      {/* 스낵바 (알림) */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          severity={snackbar.severity}
          variant="filled"
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </ThemeProvider>
  );
}

export default App;
