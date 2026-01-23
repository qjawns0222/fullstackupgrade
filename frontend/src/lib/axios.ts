import axios, { AxiosError, AxiosRequestConfig } from 'axios';
import { dispatchToast } from './toast-event';

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 요청 인터셉터
api.interceptors.request.use(
    (config) => {
        if (typeof window !== 'undefined') {
            const token = localStorage.getItem('accessToken');
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
    failedQueue.forEach((prom) => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token);
        }
    });
    failedQueue = [];
};

// 응답 인터셉터
api.interceptors.response.use(
    (response) => {
        return response;
    },
    async (error: AxiosError) => {
        const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

        if (error.response?.status === 401 && !originalRequest._retry) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                })
                    .then((token) => {
                        if (originalRequest.headers) {
                            originalRequest.headers.Authorization = `Bearer ${token}`;
                        }
                        return api(originalRequest);
                    })
                    .catch((err) => {
                        return Promise.reject(err);
                    });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const refreshToken = localStorage.getItem('refreshToken');
                if (!refreshToken) {
                    throw new Error('No refresh token available');
                }

                // 토큰 재발급 요청 (axios 인스턴스가 아닌 기본 axios 사용)
                // baseURL이 다를 수 있으므로 풀 URL 사용 추천하나, 여기서는 환경변수나 하드코딩 사용
                const baseURL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api';
                const { data } = await axios.post(`${baseURL}/auth/reissue`, {}, {
                    headers: { Authorization: refreshToken }
                });

                const { accessToken, refreshToken: newRefreshToken } = data;
                localStorage.setItem('accessToken', accessToken);
                localStorage.setItem('refreshToken', newRefreshToken);

                api.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;

                processQueue(null, accessToken);

                if (originalRequest.headers) {
                    originalRequest.headers.Authorization = `Bearer ${accessToken}`;
                }

                return api(originalRequest);
            } catch (err) {
                processQueue(err, null);

                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');

                // 로그인 페이지로 리다이렉트
                if (typeof window !== 'undefined') {
                    window.location.href = '/login';
                }

                return Promise.reject(err);
            } finally {
                isRefreshing = false;
            }
        }

        let message = '알 수 없는 오류가 발생했습니다.';
        if (error.response) {
            const data = error.response.data as any;
            message = data?.message || data?.error || `오류: ${error.response.statusText}`;
        } else if (error.request) {
            message = '서버로부터 응답이 없습니다. 네트워크를 확인해주세요.';
        } else {
            message = error.message;
        }

        // 401 에러가 아니고, 리프레시 로직에서 처리되지 않은 에러만 토스트 표시
        // (리프레시 실패 시에는 로그인 페이지로 이동하므로 토스트 굳이 안 띄워도 됨, 혹은 띄워도 됨)
        if (error.response?.status !== 401) {
            dispatchToast(message, 'error');
        }

        return Promise.reject(error);
    }
);

export default api;
