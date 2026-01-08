import axios, { AxiosError } from 'axios';
import { dispatchToast } from './toast-event';

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api', // 백엔드 API 주소 확인 필요
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 요청 인터셉터 (필요 시 토큰 추가 등)
api.interceptors.request.use(
    (config) => {
        // 예: const token = localStorage.getItem('token');
        // if (token) config.headers.Authorization = `Bearer ${token}`;
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 응답 인터셉터
api.interceptors.response.use(
    (response) => {
        return response;
    },
    (error: AxiosError) => {
        let message = '알 수 없는 오류가 발생했습니다.';

        if (error.response) {
            // 서버에서 응답이 왔으나 상태 코드가 2xx가 아님
            const data = error.response.data as any;
            // 백엔드 에러 응답 구조에 따라 조정 필요
            message = data?.message || data?.error || `오류: ${error.response.statusText}`;
        } else if (error.request) {
            // 요청은 갔으나 응답이 없음
            message = '서버로부터 응답이 없습니다. 네트워크를 확인해주세요.';
        } else {
            // 요청 설정 중 오류 발생
            message = error.message;
        }

        // 냉철하게 에러 토스트 발생
        dispatchToast(message, 'error');

        return Promise.reject(error);
    }
);

export default api;
