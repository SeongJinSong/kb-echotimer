import { useState, useEffect, useCallback, useRef } from 'react';
import { TimerResponse, AnyTimerEvent, CreateTimerRequest } from '../types/timer';
import { TimerApiService } from '../services/api';
import { webSocketService } from '../services/websocket';

/**
 * 타이머 관리를 위한 React Hook
 * REST API와 WebSocket을 통합하여 타이머 상태 관리
 */

export interface UseTimerOptions {
  timerId?: string;
  userId: string;
  autoConnect?: boolean; // WebSocket 자동 연결 여부
  isShareToken?: boolean; // 공유 토큰 여부
}

export interface UseTimerReturn {
  // 타이머 상태
  timer: TimerResponse | null;
  loading: boolean;
  error: string | null;
  connected: boolean; // WebSocket 연결 상태
  
  // 타이머 액션
  createTimer: (targetTimeSeconds: number) => Promise<void>;
  loadTimer: (timerId: string) => Promise<void>;
  saveTimestamp: () => Promise<void>;
  changeTargetTime: (newTargetTime: Date) => Promise<void>;
  completeTimer: () => Promise<void>;
  
  // WebSocket 관리
  connect: () => void;
  disconnect: () => void;
  
  // 실시간 데이터
  remainingSeconds: number;
  isCompleted: boolean;
  progress: number; // 0-100 사이의 진행률
}

export function useTimer(options: UseTimerOptions): UseTimerReturn {
  const { timerId: initialTimerId, userId, autoConnect = true, isShareToken = false } = options;
  
  // 상태 관리
  const [timer, setTimer] = useState<TimerResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  
  // Ref로 타이머 ID 관리 (WebSocket 구독용)
  const currentTimerIdRef = useRef<string | null>(initialTimerId || null);
  const intervalRef = useRef<number | null>(null);
  const timerRef = useRef<TimerResponse | null>(null);

  /**
   * 에러 처리 헬퍼
   */
  const handleError = useCallback((err: any, context: string) => {
    console.error(`❌ ${context} 오류:`, err);
    const message = err.response?.data?.message || err.message || '알 수 없는 오류가 발생했습니다.';
    setError(`${context}: ${message}`);
  }, []);

  /**
   * 새로운 타이머 생성
   */
  const createTimer = useCallback(async (targetTimeSeconds: number) => {
    setLoading(true);
    setError(null);
    
    try {
      const request: CreateTimerRequest = {
        targetTimeSeconds,
        ownerId: userId
      };
      
      const newTimer = await TimerApiService.createTimer(request);
      setTimer(newTimer);
      currentTimerIdRef.current = newTimer.timerId;
      timerRef.current = newTimer; // ref에도 저장
      
      // WebSocket 구독 시작
      console.log('🔄 타이머 생성 후 구독 시도 - connected:', connected, 'timerId:', newTimer.timerId);
      if (connected) {
        webSocketService.subscribeToTimer(newTimer.timerId, userId);
      } else {
        console.log('⚠️ WebSocket이 아직 연결되지 않음 - 연결 후 자동 구독됨');
      }
      
      console.log('✅ 타이머 생성 완료:', newTimer.timerId);
    } catch (err) {
      handleError(err, '타이머 생성');
    } finally {
      setLoading(false);
    }
  }, [userId, connected, handleError]);

  /**
   * 기존 타이머 로드
   */
  const loadTimer = useCallback(async (timerIdOrToken: string) => {
    setLoading(true);
    setError(null);
    
    try {
      let timerData;
      
      // 공유 토큰 여부 확인
      if (isShareToken) {
        // 공유 토큰으로 조회
        timerData = await TimerApiService.getTimerInfoByShareToken(timerIdOrToken, userId);
      } else {
        // 타이머 ID로 조회
        timerData = await TimerApiService.getTimerInfo(timerIdOrToken, userId);
      }
      
      setTimer(timerData);
      currentTimerIdRef.current = timerData.timerId; // 실제 타이머 ID 사용
      timerRef.current = timerData; // ref에도 저장
      
      // WebSocket 구독 시작
      if (connected) {
        webSocketService.subscribeToTimer(timerData.timerId, userId);
      }
      
      console.log('✅ 타이머 로드 완료:', timerData.timerId);
    } catch (err) {
      handleError(err, '타이머 로드');
    } finally {
      setLoading(false);
    }
  }, [userId, connected, handleError, isShareToken]);

  /**
   * 타임스탬프 저장
   */
  const saveTimestamp = useCallback(async () => {
    if (!timer) return;
    
    try {
      const request = {
        userId,
        targetTime: timer.targetTime,
        metadata: {
          savedAt: new Date().toISOString(),
          userAgent: navigator.userAgent,
          remainingSeconds
        }
      };
      
      // WebSocket으로 실시간 전송
      webSocketService.saveTimestamp(timer.timerId, request);
      
      console.log('✅ 타임스탬프 저장 요청 전송');
    } catch (err) {
      handleError(err, '타임스탬프 저장');
    }
  }, [timer, userId, remainingSeconds, handleError]);

  /**
   * 목표 시간 변경
   */
  const changeTargetTime = useCallback(async (newTargetTime: Date) => {
    if (!timer) return;
    
    try {
      const request = {
        newTargetTime: newTargetTime.toISOString(),
        changedBy: userId
      };
      
      // WebSocket으로 실시간 전송
      webSocketService.changeTargetTime(timer.timerId, request);
      
      console.log('✅ 목표 시간 변경 요청 전송');
    } catch (err) {
      handleError(err, '목표 시간 변경');
    }
  }, [timer, userId, handleError]);

  /**
   * 타이머 완료 처리
   */
  const completeTimer = useCallback(async () => {
    if (!timer) return;
    
    try {
      webSocketService.completeTimer(timer.timerId);
      console.log('✅ 타이머 완료 알림 전송');
    } catch (err) {
      handleError(err, '타이머 완료');
    }
  }, [timer, handleError]);

  /**
   * WebSocket 연결
   */
  const connect = useCallback(() => {
    webSocketService.connect();
  }, []);

  /**
   * WebSocket 연결 해제
   */
  const disconnect = useCallback(() => {
    webSocketService.disconnect();
  }, []);

  /**
   * WebSocket 이벤트 핸들러
   */
  const handleTimerEvent = useCallback((event: AnyTimerEvent) => {
    console.log('📨 타이머 이벤트 처리:', event.eventType);
    
    switch (event.eventType) {
      case 'TARGET_TIME_CHANGED':
        // 목표 시간이 변경되었을 때 타이머 정보 새로고침
        if (currentTimerIdRef.current) {
          loadTimer(currentTimerIdRef.current);
        }
        break;
        
      case 'TIMESTAMP_SAVED':
        // 타임스탬프가 저장되었을 때 (다른 사용자가 저장한 경우)
        console.log('📝 타임스탬프 저장됨:', event);
        break;
        
      case 'USER_JOINED':
        // 사용자가 참여했을 때
        console.log('👋 사용자 참여:', event);
        break;
        
      case 'USER_LEFT':
        // 사용자가 나갔을 때
        console.log('👋 사용자 퇴장:', event);
        break;
        
      case 'TIMER_COMPLETED':
        // 타이머가 완료되었을 때
        console.log('🎉 타이머 완료:', event);
        if (timerRef.current) {
          const updatedTimer = { ...timerRef.current, completed: true };
          setTimer(updatedTimer);
          timerRef.current = updatedTimer;
        }
        break;
        
      case 'ONLINE_USER_COUNT_UPDATED':
        // 온라인 사용자 수가 업데이트되었을 때
        const countEvent = event as import('../types/timer').OnlineUserCountUpdatedEvent;
        console.log('👥 온라인 사용자 수 업데이트:', countEvent);
        console.log('📊 현재 타이머 상태:', timerRef.current);
        console.log('🔢 새로운 사용자 수:', countEvent.onlineUserCount);
        
        if (timerRef.current && countEvent.onlineUserCount !== undefined) {
          const oldCount = timerRef.current.onlineUserCount;
          const newCount = countEvent.onlineUserCount;
          
          const updatedTimer = { 
            ...timerRef.current, 
            onlineUserCount: newCount
          };
          
          console.log('🔄 사용자 수 업데이트:', oldCount, '→', newCount);
          setTimer(updatedTimer);
          timerRef.current = updatedTimer;
        } else {
          console.log('❌ 타이머 상태 업데이트 실패 - timerRef.current:', timerRef.current, 'onlineUserCount:', countEvent.onlineUserCount);
        }
        break;
    }
  }, [loadTimer]); // timer 의존성 제거

  /**
   * 남은 시간 계산 (1초마다 업데이트)
   */
  useEffect(() => {
    if (!timer || timer.completed) {
      setRemainingSeconds(0);
      return;
    }

    const updateRemainingTime = () => {
      const now = new Date().getTime();
      const target = new Date(timer.targetTime).getTime();
      const remaining = Math.max(0, Math.floor((target - now) / 1000));
      
      setRemainingSeconds(remaining);
      
      // 타이머가 완료되었을 때
      if (remaining === 0 && !timer.completed) {
        completeTimer();
      }
    };

    // 즉시 실행
    updateRemainingTime();
    
    // 1초마다 업데이트
    intervalRef.current = setInterval(updateRemainingTime, 1000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [timer, completeTimer]);

  /**
   * WebSocket 연결 상태 관리
   */
  useEffect(() => {
    const handleConnectionStatus = (isConnected: boolean) => {
      setConnected(isConnected);
      
      // 연결되었고 타이머가 있으면 구독 시작
      if (isConnected && currentTimerIdRef.current) {
        console.log('🔄 WebSocket 연결됨 - 자동 구독 시작:', currentTimerIdRef.current);
        webSocketService.subscribeToTimer(currentTimerIdRef.current, userId);
      } else {
        console.log('🔍 WebSocket 연결 상태 - isConnected:', isConnected, 'timerId:', currentTimerIdRef.current);
      }
    };

    // 연결 상태 리스너 등록
    webSocketService.addConnectionStatusListener(handleConnectionStatus);
    
    // 이벤트 핸들러 등록
    webSocketService.addEventListener('*', handleTimerEvent);
    
    // 자동 연결
    if (autoConnect) {
      webSocketService.connect();
    }

    return () => {
      // 리스너 제거
      webSocketService.removeConnectionStatusListener(handleConnectionStatus);
      webSocketService.removeEventListener('*', handleTimerEvent);
      
      // 구독 해제
      if (currentTimerIdRef.current) {
        webSocketService.unsubscribeFromTimer(currentTimerIdRef.current);
      }
    };
  }, [userId, autoConnect]); // handleTimerEvent 의존성 제거

  /**
   * 초기 타이머 로드
   */
  useEffect(() => {
    if (initialTimerId) {
      loadTimer(initialTimerId);
    }
  }, [initialTimerId, loadTimer]);

  // 계산된 값들
  const isCompleted = timer?.completed || remainingSeconds === 0;
  const progress = timer ? 
    Math.max(0, Math.min(100, ((timer.remainingTime - remainingSeconds) / timer.remainingTime) * 100)) : 0;

  return {
    // 상태
    timer,
    loading,
    error,
    connected,
    
    // 액션
    createTimer,
    loadTimer, // 타이머 새로고침용
    saveTimestamp,
    changeTargetTime,
    completeTimer,
    connect,
    disconnect,
    
    // 계산된 값
    remainingSeconds,
    isCompleted,
    progress
  };
}
