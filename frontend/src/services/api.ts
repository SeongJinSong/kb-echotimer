import axios, { AxiosResponse } from 'axios';
import { 
  TimerResponse, 
  CreateTimerRequest, 
  SaveTimestampRequest, 
  ChangeTargetTimeRequest,
  TimestampEntry 
} from '../types/timer';

/**
 * REST API 클라이언트 서비스
 * 백엔드와의 HTTP 통신을 담당
 */

// Axios 인스턴스 생성 (기본 설정)
const apiClient = axios.create({
  baseURL: '/api/v1', // Vite 프록시를 통해 백엔드로 전달됨
  timeout: 10000, // 10초 타임아웃
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 (로깅 및 인증 토큰 추가 등)
apiClient.interceptors.request.use(
  (config) => {
    console.log(`🚀 API 요청: ${config.method?.toUpperCase()} ${config.url}`);
    // TODO: 인증 토큰이 있다면 여기서 추가
    // config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => {
    console.error('❌ API 요청 오류:', error);
    return Promise.reject(error);
  }
);

// 응답 인터셉터 (에러 처리 및 로깅)
apiClient.interceptors.response.use(
  (response) => {
    console.log(`✅ API 응답: ${response.status} ${response.config.url}`);
    return response;
  },
  (error) => {
    console.error('❌ API 응답 오류:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

/**
 * 타이머 API 서비스
 */
export class TimerApiService {
  
  /**
   * 새로운 타이머 생성
   * @param request 타이머 생성 요청 데이터
   * @returns 생성된 타이머 정보
   */
  static async createTimer(request: CreateTimerRequest): Promise<TimerResponse> {
    const response: AxiosResponse<TimerResponse> = await apiClient.post('/timers', request);
    return response.data;
  }

  /**
   * 특정 타이머 정보 조회
   * @param timerId 타이머 ID
   * @param userId 사용자 ID (선택적)
   * @returns 타이머 정보
   */
  static async getTimerInfo(timerId: string, userId?: string): Promise<TimerResponse> {
    const params = userId ? { userId } : {};
    const response: AxiosResponse<TimerResponse> = await apiClient.get(`/timers/${timerId}`, { params });
    return response.data;
  }

  /**
   * 공유 토큰으로 타이머 정보 조회
   * @param shareToken 공유 토큰
   * @param userId 사용자 ID (선택적)
   * @returns 타이머 정보
   */
  static async getTimerInfoByShareToken(shareToken: string, userId?: string): Promise<TimerResponse> {
    const params = userId ? { userId } : {};
    const response: AxiosResponse<TimerResponse> = await apiClient.get(`/timers/shared/${shareToken}`, { params });
    return response.data;
  }

  /**
   * 타이머의 목표 시간 변경
   * @param timerId 타이머 ID
   * @param request 목표 시간 변경 요청 데이터
   * @returns 업데이트된 타이머 정보
   */
  static async changeTargetTime(timerId: string, request: ChangeTargetTimeRequest): Promise<TimerResponse> {
    const response: AxiosResponse<TimerResponse> = await apiClient.put(`/timers/${timerId}/target-time`, request);
    return response.data;
  }

  /**
   * 타임스탬프 저장
   * @param timerId 타이머 ID
   * @param request 타임스탬프 저장 요청 데이터
   * @returns 저장된 타임스탬프 엔트리
   */
  static async saveTimestamp(timerId: string, request: SaveTimestampRequest): Promise<TimestampEntry> {
    const response: AxiosResponse<TimestampEntry> = await apiClient.post(`/timers/${timerId}/timestamps`, request);
    return response.data;
  }

  /**
   * 타이머 히스토리 조회
   * @param timerId 타이머 ID
   * @returns 타임스탬프 엔트리 목록
   */
  static async getTimerHistory(timerId: string): Promise<TimestampEntry[]> {
    const response: AxiosResponse<TimestampEntry[]> = await apiClient.get(`/timers/${timerId}/history`);
    return response.data;
  }
}

/**
 * API 에러 처리 유틸리티
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status?: number,
    public data?: any
  ) {
    super(message);
    this.name = 'ApiError';
  }

  static fromAxiosError(error: any): ApiError {
    if (error.response) {
      // 서버에서 응답을 받았지만 에러 상태코드
      return new ApiError(
        error.response.data?.message || '서버 오류가 발생했습니다.',
        error.response.status,
        error.response.data
      );
    } else if (error.request) {
      // 요청은 보냈지만 응답을 받지 못함
      return new ApiError('서버에 연결할 수 없습니다. 네트워크를 확인해주세요.');
    } else {
      // 요청 설정 중 오류 발생
      return new ApiError('요청 처리 중 오류가 발생했습니다.');
    }
  }
}

export default apiClient;
