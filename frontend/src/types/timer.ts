/**
 * 타이머 관련 타입 정의
 * 백엔드 DTO와 일치하도록 구성
 */

export interface TimerResponse {
  timerId: string;
  userId?: string;
  targetTime: string; // ISO 8601 형식
  serverTime: string; // ISO 8601 형식
  remainingTime: number; // 초 단위
  completed: boolean;
  ownerId: string;
  onlineUserCount: number;
  onlineUsers?: string[];
  shareToken?: string;
  userRole: 'OWNER' | 'VIEWER';
  savedAt?: string; // ISO 8601 형식
  metadata?: Record<string, any>;
}

export interface CreateTimerRequest {
  targetTimeSeconds: number;
  ownerId: string;
}

export interface SaveTimestampRequest {
  userId: string;
  targetTime: string; // ISO 8601 형식
  metadata?: Record<string, any>;
}

export interface ChangeTargetTimeRequest {
  newTargetTime: string; // ISO 8601 형식
  changedBy: string;
}

export interface TimestampEntry {
  id: string;
  timerId: string;
  userId: string;
  savedAt: string; // ISO 8601 형식
  remainingTime: number; // 밀리초 단위
  targetTime: string; // ISO 8601 형식
  metadata?: Record<string, any>;
  createdAt: string; // ISO 8601 형식
}

// WebSocket 이벤트 타입들
export interface TimerEvent {
  eventId: string;
  timerId: string;
  timestamp: string; // ISO 8601 형식
  originServerId: string;
  eventType: string;
}

export interface TargetTimeChangedEvent extends TimerEvent {
  eventType: 'TARGET_TIME_CHANGED';
  oldTargetTime: string;
  newTargetTime: string;
  changedBy: string;
  serverTime: string;
}

export interface TimestampSavedEvent extends TimerEvent {
  eventType: 'TIMESTAMP_SAVED';
  userId: string;
  savedAt: string;
  remainingTime: number;
  targetTime: string;
  metadata?: Record<string, any>;
}

export interface UserJoinedEvent extends TimerEvent {
  eventType: 'USER_JOINED';
  userId: string;
}

export interface UserLeftEvent extends TimerEvent {
  eventType: 'USER_LEFT';
  userId: string;
}

export interface TimerCompletedEvent extends TimerEvent {
  eventType: 'TIMER_COMPLETED';
  completedTargetTime: string;
  completedAt: string;
  ownerId: string;
  onlineUserCount: number;
}

export interface OnlineUserCountUpdatedEvent extends TimerEvent {
  eventType: 'ONLINE_USER_COUNT_UPDATED';
  onlineUserCount: number;
}

export type AnyTimerEvent = 
  | TargetTimeChangedEvent 
  | TimestampSavedEvent 
  | UserJoinedEvent 
  | UserLeftEvent 
  | TimerCompletedEvent
  | OnlineUserCountUpdatedEvent;
