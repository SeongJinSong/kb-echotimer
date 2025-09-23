import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { 
  AnyTimerEvent, 
  SaveTimestampRequest, 
  ChangeTargetTimeRequest 
} from '../types/timer';

/**
 * WebSocket (STOMP) 클라이언트 서비스
 * 실시간 타이머 이벤트 처리를 담당
 */

export type EventHandler<T = AnyTimerEvent> = (event: T) => void;
export type ConnectionStatusHandler = (connected: boolean) => void;

export class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private eventHandlers: Map<string, EventHandler[]> = new Map();
  private connectionStatusHandlers: ConnectionStatusHandler[] = [];
  private isConnected = false;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  constructor() {
    this.setupClient();
  }

  /**
   * STOMP 클라이언트 초기 설정
   */
  private setupClient(): void {
    this.client = new Client({
      // SockJS를 통한 WebSocket 연결 (Vite 프록시 사용)
      webSocketFactory: () => new SockJS('/ws'),
      
      // 연결 설정
      connectHeaders: {
        // TODO: 인증이 필요한 경우 여기에 토큰 추가
        // Authorization: `Bearer ${token}`
      },

      // 디버그 로깅
      debug: (str) => {
        console.log('🔌 STOMP:', str);
      },

      // 재연결 설정
      reconnectDelay: 5000, // 5초 후 재연결 시도
      heartbeatIncoming: 4000, // 서버로부터 하트비트 수신 간격
      heartbeatOutgoing: 4000, // 서버로 하트비트 전송 간격

      // 연결 성공 콜백
      onConnect: (frame) => {
        console.log('✅ WebSocket 연결 성공:', frame);
        this.isConnected = true;
        this.reconnectAttempts = 0;
        this.notifyConnectionStatus(true);
      },

      // 연결 실패 콜백
      onStompError: (frame) => {
        console.error('❌ STOMP 오류:', frame.headers['message']);
        console.error('상세 정보:', frame.body);
        this.isConnected = false;
        this.notifyConnectionStatus(false);
      },

      // 연결 해제 콜백
      onDisconnect: (frame) => {
        console.log('🔌 WebSocket 연결 해제:', frame);
        this.isConnected = false;
        this.notifyConnectionStatus(false);
      },

      // WebSocket 연결 실패 콜백
      onWebSocketError: (error) => {
        console.error('❌ WebSocket 오류:', error);
        this.handleReconnect();
      }
    });
  }

  /**
   * WebSocket 연결 시작
   */
  connect(): void {
    if (this.client && !this.isConnected) {
      console.log('🔌 WebSocket 연결 시도...');
      this.client.activate();
    }
  }

  /**
   * WebSocket 연결 해제
   */
  disconnect(): void {
    if (this.client && this.isConnected) {
      console.log('🔌 WebSocket 연결 해제...');
      
      // 모든 구독 해제
      this.subscriptions.forEach((subscription) => {
        subscription.unsubscribe();
      });
      this.subscriptions.clear();
      
      // 클라이언트 비활성화
      this.client.deactivate();
    }
  }

  /**
   * 재연결 처리
   */
  private handleReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`🔄 재연결 시도 ${this.reconnectAttempts}/${this.maxReconnectAttempts}...`);
      
      setTimeout(() => {
        if (!this.isConnected) {
          this.connect();
        }
      }, 5000 * this.reconnectAttempts); // 지수 백오프
    } else {
      console.error('❌ 최대 재연결 시도 횟수 초과');
    }
  }

  /**
   * 특정 타이머 토픽 구독
   * @param timerId 타이머 ID
   * @param userId 사용자 ID
   */
  subscribeToTimer(timerId: string, userId: string): void {
    if (!this.client || !this.isConnected) {
      console.warn('⚠️ WebSocket이 연결되지 않았습니다.');
      return;
    }

    const destination = `/topic/timer/${timerId}`;
    
    // 이미 구독 중인지 확인
    if (this.subscriptions.has(destination)) {
      console.log('📡 이미 구독 중인 타이머:', timerId);
      return;
    }

    console.log('📡 타이머 구독 시작:', timerId);
    
    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        console.log('📨 WebSocket 원본 메시지 수신:', message.body);
        const event: AnyTimerEvent = JSON.parse(message.body);
        console.log('📨 타이머 이벤트 수신:', event.eventType, event);
        
        // 이벤트 핸들러들에게 전달
        this.notifyEventHandlers(event.eventType, event);
        this.notifyEventHandlers('*', event); // 모든 이벤트를 받는 핸들러
      } catch (error) {
        console.error('❌ 이벤트 파싱 오류:', error, 'Original message:', message.body);
      }
    }, {
      userId: userId // 헤더로 userId 전달
    });

    this.subscriptions.set(destination, subscription);
  }

  /**
   * 타이머 구독 해제
   * @param timerId 타이머 ID
   */
  unsubscribeFromTimer(timerId: string): void {
    const destination = `/topic/timer/${timerId}`;
    const subscription = this.subscriptions.get(destination);
    
    if (subscription) {
      console.log('📡 타이머 구독 해제:', timerId);
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
    }
  }

  /**
   * 타임스탬프 저장 메시지 전송
   * @param timerId 타이머 ID
   * @param request 저장 요청 데이터
   */
  saveTimestamp(timerId: string, request: SaveTimestampRequest): void {
    if (!this.client || !this.isConnected) {
      console.warn('⚠️ WebSocket이 연결되지 않았습니다.');
      return;
    }

    const destination = `/app/timer/${timerId}/save`;
    console.log('📤 타임스탬프 저장 요청:', destination, request);
    
    this.client.publish({
      destination,
      body: JSON.stringify(request)
    });
  }

  /**
   * 목표 시간 변경 메시지 전송
   * @param timerId 타이머 ID
   * @param request 변경 요청 데이터
   */
  changeTargetTime(timerId: string, request: ChangeTargetTimeRequest): void {
    if (!this.client || !this.isConnected) {
      console.warn('⚠️ WebSocket이 연결되지 않았습니다.');
      return;
    }

    const destination = `/app/timer/${timerId}/change-target`;
    console.log('📤 목표 시간 변경 요청:', destination, request);
    
    this.client.publish({
      destination,
      body: JSON.stringify(request)
    });
  }

  /**
   * 타이머 완료 메시지 전송
   * @param timerId 타이머 ID
   */
  completeTimer(timerId: string): void {
    if (!this.client || !this.isConnected) {
      console.warn('⚠️ WebSocket이 연결되지 않았습니다.');
      return;
    }

    const destination = `/app/timer/${timerId}/complete`;
    console.log('📤 타이머 완료 알림:', destination);
    
    this.client.publish({
      destination,
      body: JSON.stringify({})
    });
  }

  /**
   * 이벤트 핸들러 등록
   * @param eventType 이벤트 타입 ('*'는 모든 이벤트)
   * @param handler 이벤트 핸들러 함수
   */
  addEventListener(eventType: string, handler: EventHandler): void {
    if (!this.eventHandlers.has(eventType)) {
      this.eventHandlers.set(eventType, []);
    }
    this.eventHandlers.get(eventType)!.push(handler);
  }

  /**
   * 이벤트 핸들러 제거
   * @param eventType 이벤트 타입
   * @param handler 제거할 핸들러 함수
   */
  removeEventListener(eventType: string, handler: EventHandler): void {
    const handlers = this.eventHandlers.get(eventType);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    }
  }

  /**
   * 연결 상태 변경 핸들러 등록
   * @param handler 연결 상태 변경 핸들러
   */
  addConnectionStatusListener(handler: ConnectionStatusHandler): void {
    this.connectionStatusHandlers.push(handler);
  }

  /**
   * 연결 상태 변경 핸들러 제거
   * @param handler 제거할 핸들러
   */
  removeConnectionStatusListener(handler: ConnectionStatusHandler): void {
    const index = this.connectionStatusHandlers.indexOf(handler);
    if (index > -1) {
      this.connectionStatusHandlers.splice(index, 1);
    }
  }

  /**
   * 이벤트 핸들러들에게 이벤트 전달
   */
  private notifyEventHandlers(eventType: string, event: AnyTimerEvent): void {
    const handlers = this.eventHandlers.get(eventType);
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(event);
        } catch (error) {
          console.error('❌ 이벤트 핸들러 오류:', error);
        }
      });
    }
  }

  /**
   * 연결 상태 변경 알림
   */
  private notifyConnectionStatus(connected: boolean): void {
    this.connectionStatusHandlers.forEach(handler => {
      try {
        handler(connected);
      } catch (error) {
        console.error('❌ 연결 상태 핸들러 오류:', error);
      }
    });
  }

  /**
   * 현재 연결 상태 반환
   */
  get connected(): boolean {
    return this.isConnected;
  }
}

// 싱글톤 인스턴스 생성
export const webSocketService = new WebSocketService();
