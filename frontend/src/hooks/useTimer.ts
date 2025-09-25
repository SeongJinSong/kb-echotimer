import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
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
  onTimerCompleted?: (isOwner: boolean) => void; // 타이머 완료 콜백 (소유자 여부 전달)
  onSharedTimerAccessed?: (accessedUserId: string) => void; // 공유 타이머 접속 콜백 추가
}

export interface UseTimerReturn {
  // 타이머 상태
  timer: TimerResponse | null;
  loading: boolean;
  error: string | null;
  connected: boolean; // WebSocket 연결 상태
  
  // 타이머 액션
  createTimer: (targetTimeSeconds: number) => Promise<TimerResponse>;
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
  const { timerId: initialTimerId, userId, autoConnect = true, isShareToken = false, onTimerCompleted, onSharedTimerAccessed } = options;
  
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
  const hasNotifiedCompletion = useRef<boolean>(false); // 완료 알림 중복 방지

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
      
      // 완료 알림 상태 초기화 (새로운 타이머 생성 시)
      hasNotifiedCompletion.current = false;
      console.log('🔄 완료 알림 상태 초기화 (새 타이머 생성)');
      
      // WebSocket 구독 시작
      console.log('🔄 타이머 생성 후 구독 시도 - connected:', connected, 'timerId:', newTimer.timerId);
      if (connected) {
        webSocketService.subscribeToTimer(newTimer.timerId, userId);
      } else {
        console.log('⚠️ WebSocket이 아직 연결되지 않음 - 연결 후 자동 구독됨');
      }
      
      console.log('✅ 타이머 생성 완료:', newTimer.timerId);
      return newTimer; // 생성된 타이머 반환
    } catch (err) {
      handleError(err, '타이머 생성');
      throw err; // 에러를 다시 던져서 상위에서 처리할 수 있도록
    } finally {
      setLoading(false);
    }
  }, [userId, connected, handleError]);

  /**
   * 기존 타이머 로드
   */
  const loadTimer = useCallback(async (timerIdOrToken: string) => {
    console.log('🔍 loadTimer 호출됨:', { timerIdOrToken, isShareToken, userId });
    
    // timerIdOrToken이 없으면 로드하지 않음
    if (!timerIdOrToken) {
      console.log('⚠️ loadTimer: timerIdOrToken이 없어서 로드하지 않음');
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      let timerData;
      
      console.log('🔍 API 호출 전 상태:', { 
        timerIdOrToken, 
        isShareToken, 
        userId,
        'timerIdOrToken 길이': timerIdOrToken.length,
        'UUID 형태인가': /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(timerIdOrToken)
      });
      
      // 공유 토큰 여부 확인
      if (isShareToken) {
        console.log('📤 공유 토큰으로 API 호출:', timerIdOrToken);
        // 공유 토큰으로 조회
        timerData = await TimerApiService.getTimerInfoByShareToken(timerIdOrToken, userId);
      } else {
        console.log('📤 타이머 ID로 API 호출:', timerIdOrToken);
        // 타이머 ID로 조회
        timerData = await TimerApiService.getTimerInfo(timerIdOrToken, userId);
      }
      
      console.log('📥 API 응답:', timerData);
      
            // 타이머 데이터 유효성 체크
            if (!timerData.timerId) {
              console.log('⚠️ 유효하지 않은 타이머 응답 데이터:', timerData);
              console.log('🔄 sessionStorage에서 타이머 ID 제거 및 초기화');
              
              // sessionStorage에서 타이머 관련 정보 제거
              sessionStorage.removeItem('kb-echotimer-current-timer-id');
              sessionStorage.removeItem('kb-echotimer-is-share-token');
              
              // 상태 초기화
              setTimer(null);
              currentTimerIdRef.current = null;
              timerRef.current = null;
              
              // 부모 컴포넌트에 초기화 신호 전달 (App.tsx에서 처리)
              if (typeof window !== 'undefined') {
                window.dispatchEvent(new CustomEvent('timer-invalid', { 
                  detail: { reason: 'invalid_response' } 
                }));
              }
              
              return;
            }

            // 완료된 타이머 체크 - 소유자와 공유자 모두 유지
            if (timerData.completed) {
              console.log('✅ 완료된 타이머 감지 - 유지:', timerData.timerId, 'userRole:', timerData.userRole);
              // 소유자와 공유자 모두 완료된 타이머를 계속 볼 수 있음
            }
      
      setTimer(timerData);
      currentTimerIdRef.current = timerData.timerId; // 실제 타이머 ID 사용
      timerRef.current = timerData; // ref에도 저장
      
      // 완료 알림 상태 초기화 (새로운 타이머 로드 시)
      hasNotifiedCompletion.current = false;
      console.log('🔄 완료 알림 상태 초기화');
      
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
   * 타이머 ID로 강제 로드 (항상 타이머 ID API 사용)
   */
  const loadTimerById = useCallback(async (timerId: string) => {
    console.log('🔍 loadTimerById 호출됨:', { timerId, userId });
    
    if (!timerId) {
      console.log('⚠️ loadTimerById: timerId가 없어서 로드하지 않음');
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      console.log('📤 타이머 ID로 강제 API 호출:', timerId);
      // 항상 타이머 ID로 조회
      const timerData = await TimerApiService.getTimerInfo(timerId, userId);
      
      console.log('📥 API 응답:', timerData);
      
      // 타이머 데이터 유효성 체크
      if (!timerData.timerId) {
        console.log('⚠️ 유효하지 않은 타이머 응답 데이터:', timerData);
        handleError(new Error('Invalid timer response'), '타이머 로드 (강제 타이머 ID)');
        return;
      }
      
      // 타이머 상태 업데이트
      setTimer(timerData);
      timerRef.current = timerData;
      currentTimerIdRef.current = timerData.timerId;
      
      // WebSocket 구독 (이미 구독 중이면 무시됨)
      if (connected && webSocketService) {
        webSocketService.subscribeToTimer(timerData.timerId, userId);
      }
      
      console.log('✅ 타이머 로드 완료 (강제 타이머 ID):', timerData.timerId);
    } catch (err) {
      console.error('❌ 타이머 로드 실패 (강제 타이머 ID):', err);
      handleError(err as Error, '타이머 로드 (강제 타이머 ID)');
    } finally {
      setLoading(false);
    }
  }, [userId, connected, handleError, webSocketService]);

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
   * 타이머 정지 처리
   */
  const completeTimer = useCallback(async () => {
    if (!timer) return;
    
    try {
      // 1. 화면에서 먼저 타이머를 정지 상태로 변경
      setTimer(prev => prev ? { ...prev, completed: true } : null);
      setRemainingSeconds(0);
      
      // 2. 그 다음 서버에 정지 알림 전송
      webSocketService.completeTimer(timer.timerId, userId);
      console.log('✅ 타이머 정지: 화면 업데이트 완료, 서버 알림 전송');
    } catch (err) {
      handleError(err, '타이머 정지');
    }
  }, [timer, userId, handleError]);

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
        console.log('🔄 목표 시간 변경 이벤트 수신:', event);
        if (event.timerId) {
          console.log('🔄 타이머 정보 새로고침 시작 (이벤트의 타이머 ID 사용):', event.timerId);
          // TARGET_TIME_CHANGED 이벤트에서는 항상 타이머 ID로 API 호출
          loadTimerById(event.timerId);
        } else if (currentTimerIdRef.current) {
          console.log('🔄 타이머 정보 새로고침 시작 (currentTimerIdRef 사용):', currentTimerIdRef.current);
          loadTimer(currentTimerIdRef.current);
        } else {
          console.log('❌ 타이머 ID를 찾을 수 없음');
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
        // 타이머가 완료되었을 때 (소유자/공유자 구분하여 알림 표시)
        console.log('🎉 타이머 완료 이벤트 수신:', event);
        console.log('👤 현재 사용자 ID:', userId);
        console.log('📊 현재 타이머 상태:', timerRef.current);
        console.log('🔔 완료 알림 표시 여부:', hasNotifiedCompletion.current);
        
        if (timerRef.current) {
          const updatedTimer = { ...timerRef.current, completed: true };
          setTimer(updatedTimer);
          timerRef.current = updatedTimer;
          
          // 완료 알림이 아직 표시되지 않은 경우에만 처리
          if (!hasNotifiedCompletion.current) {
            hasNotifiedCompletion.current = true; // 완료 알림 표시됨을 기록
            
            // 소유자 여부 확인
            const isOwner = timerRef.current.ownerId === userId;
            console.log('👑 소유자 여부:', isOwner, '소유자 ID:', timerRef.current.ownerId, '현재 사용자 ID:', userId);
            
            // 완료 콜백 호출 (소유자/공유자 구분하여 알림 표시)
            if (onTimerCompleted) {
              console.log('📞 서버 측 타이머 완료 콜백 호출 중... (소유자:', isOwner, ')');
              onTimerCompleted(isOwner);
            } else {
              console.log('❌ 타이머 완료 콜백이 없음');
            }
          } else {
            console.log('⚠️ 이미 완료 알림이 표시되어 중복 처리 방지');
          }
        } else {
          console.log('❌ 타이머 상태가 없어서 완료 처리 불가');
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
        
      case 'SHARED_TIMER_ACCESSED':
        // 공유 타이머 접속 이벤트 (소유자에게만 알림 표시)
        const accessEvent = event as import('../types/timer').SharedTimerAccessedEvent;
        console.log('🔗 공유 타이머 접속 이벤트 수신:', accessEvent);
        console.log('📊 현재 타이머 상태:', timerRef.current);
        console.log('👤 현재 사용자 ID:', userId);
        console.log('👑 타이머 소유자 ID:', timerRef.current?.ownerId);
        
        // 현재 사용자가 소유자인 경우에만 알림 표시
        if (timerRef.current && timerRef.current.ownerId === userId) {
          console.log('🔔 소유자에게 공유 타이머 접속 알림 표시:', accessEvent.accessedUserId);
          // 알림 콜백 호출 (App.tsx에서 전달받은 콜백)
          if (onSharedTimerAccessed) {
            console.log('📞 알림 콜백 호출 중...');
            onSharedTimerAccessed(accessEvent.accessedUserId);
          } else {
            console.log('❌ 알림 콜백이 없음');
          }
        } else {
          console.log('👤 소유자가 아니므로 알림 표시하지 않음 - 현재사용자:', userId, '소유자:', timerRef.current?.ownerId);
        }
        break;
    }
  }, [loadTimer, onTimerCompleted, onSharedTimerAccessed, userId]); // 의존성 추가

  /**
   * 남은 시간 계산 (1초마다 업데이트)
   */
  useEffect(() => {
    if (!timer || timer.completed) {
      setRemainingSeconds(0);
      return;
    }

    let hasCompletedOnce = false; // 완료 이벤트 중복 방지

    const updateRemainingTime = () => {
      const now = new Date().getTime();
      const target = new Date(timer.targetTime).getTime();
      const remaining = Math.max(0, Math.floor((target - now) / 1000));
      
      setRemainingSeconds(remaining);
      
      // 타이머가 완료되었을 때 (한 번만 실행)
      if (remaining === 0 && !hasCompletedOnce && !hasNotifiedCompletion.current) {
        hasCompletedOnce = true;
        hasNotifiedCompletion.current = true; // 완료 알림 표시됨을 기록
        
        console.log('🎉 클라이언트 측 타이머 완료 감지');
        console.log('👤 현재 사용자 ID:', userId);
        console.log('📊 타이머 정보:', timer);
        
        // 소유자 여부 확인
        const isOwner = timer.ownerId === userId;
        console.log('👑 소유자 여부:', isOwner, '소유자 ID:', timer.ownerId, '현재 사용자 ID:', userId);
        
        // 완료 콜백 호출 (소유자/공유자 구분하여 알림 표시)
        if (onTimerCompleted) {
          console.log('📞 클라이언트 측 타이머 완료 콜백 호출 중... (소유자:', isOwner, ')');
          onTimerCompleted(isOwner);
        } else {
          console.log('❌ 클라이언트 측 타이머 완료 콜백이 없음');
        }
        
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
  }, [timer, userId, onTimerCompleted, completeTimer]);

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
    console.log('🔍 useEffect - initialTimerId 체크:', { 
      initialTimerId, 
      hasInitialTimerId: !!initialTimerId, 
      currentTimer: !!timer,
      currentTimerIdRef: currentTimerIdRef.current 
    });
    
    if (initialTimerId) {
      // initialTimerId가 있으면 로드
      console.log('🔄 useEffect에서 loadTimer 호출:', initialTimerId);
      loadTimer(initialTimerId);
    } else if (currentTimerIdRef.current && !timer) {
      // initialTimerId는 없지만 ref에 타이머 ID가 있고 timer 상태가 없다면 복구 시도
      console.log('🔄 ref에서 타이머 ID 복구 시도:', currentTimerIdRef.current);
      loadTimer(currentTimerIdRef.current);
    } else if (!initialTimerId && !currentTimerIdRef.current) {
      // 둘 다 없다면 완전 초기화
      console.log('🔄 타이머 상태 완전 초기화');
      setTimer(null);
      timerRef.current = null;
    } else {
      console.log('⚠️ useEffect - 조건에 맞지 않아 아무 작업 안함');
    }
  }, [initialTimerId, loadTimer]);

  // 계산된 값들
  const isCompleted = timer?.completed || remainingSeconds === 0;
  
  // 진행률 계산 (남은 시간 기준으로 단순화)
  const progress = useMemo(() => {
    if (!timer || !timer.targetTime || isCompleted) return 100;
    
    const now = new Date().getTime();
    const target = new Date(timer.targetTime).getTime();
    const serverTime = new Date(timer.serverTime).getTime();
    
    // 서버 시간을 기준으로 한 남은 시간 (밀리초)
    const serverRemainingMs = target - serverTime;
    // 현재 시간을 기준으로 한 남은 시간 (밀리초)  
    const currentRemainingMs = target - now;
    
    if (serverRemainingMs <= 0) return 100; // 이미 완료
    
    // 진행률 = (서버 기준 남은 시간 - 현재 기준 남은 시간) / 서버 기준 남은 시간 * 100
    const progressPercent = Math.max(0, Math.min(100, 
      ((serverRemainingMs - currentRemainingMs) / serverRemainingMs) * 100
    ));
    
    return progressPercent;
  }, [timer, remainingSeconds, isCompleted]); // remainingSeconds를 의존성에 추가하여 실시간 업데이트

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
