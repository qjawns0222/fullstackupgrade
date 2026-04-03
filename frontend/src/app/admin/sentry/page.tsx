'use client';

import { useState, useEffect } from 'react';
import api from '@/lib/axios';

interface SentryHealth {
  sentryEnabled: boolean;
  status: string;
}

export default function SentryMonitoringPage() {
  const [health, setHealth] = useState<SentryHealth | null>(null);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchHealth = async () => {
    try {
      const res = await api.get('/sentry/health');
      setHealth(res.data);
    } catch {
      setHealth(null);
    }
  };

  const sendTestEvent = async () => {
    setLoading(true);
    setTestResult(null);
    try {
      const res = await api.post('/sentry/test-error');
      setTestResult(`이벤트 전송 완료 — Event ID: ${res.data.sentryEventId}`);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Unknown error';
      setTestResult(`전송 실패: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  const triggerFrontendError = () => {
    // 프론트엔드 Sentry 연동 테스트 — 실제 예외를 발생시켜 ErrorBoundary가 캡처
    throw new Error('Sentry 프론트엔드 테스트 에러');
  };

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold mb-2">Sentry 에러 추적 대시보드</h1>
      <p className="text-gray-500 mb-8">
        백엔드/프론트엔드 에러 캡처 상태와 세션 리플레이 설정을 확인합니다.
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        {/* 백엔드 연결 상태 */}
        <div className="bg-white p-6 rounded-lg shadow border border-gray-200">
          <h2 className="text-lg font-semibold mb-4 text-gray-800">백엔드 Sentry 연결 상태</h2>
          {health ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-gray-600">SDK 초기화</span>
                <span className={`px-2 py-1 rounded text-sm font-medium ${
                  health.sentryEnabled
                    ? 'bg-green-100 text-green-700'
                    : 'bg-yellow-100 text-yellow-700'
                }`}>
                  {health.sentryEnabled ? '활성' : '비활성 (DSN 미설정)'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-600">상태</span>
                <span className="text-gray-800 font-mono text-sm">{health.status}</span>
              </div>
            </div>
          ) : (
            <div className="text-gray-400 text-sm">백엔드 연결 중...</div>
          )}
        </div>

        {/* 프론트엔드 Sentry 설정 */}
        <div className="bg-white p-6 rounded-lg shadow border border-gray-200">
          <h2 className="text-lg font-semibold mb-4 text-gray-800">프론트엔드 Sentry 설정</h2>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-gray-600">DSN 설정</span>
              <span className={`px-2 py-1 rounded text-sm font-medium ${
                process.env.NEXT_PUBLIC_SENTRY_DSN
                  ? 'bg-green-100 text-green-700'
                  : 'bg-yellow-100 text-yellow-700'
              }`}>
                {process.env.NEXT_PUBLIC_SENTRY_DSN ? '설정됨' : '미설정'}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-600">세션 리플레이</span>
              <span className="px-2 py-1 rounded text-sm font-medium bg-blue-100 text-blue-700">
                에러 시 100% 녹화
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-600">ErrorBoundary</span>
              <span className="px-2 py-1 rounded text-sm font-medium bg-green-100 text-green-700">
                적용됨
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 테스트 액션 */}
      <div className="bg-white p-6 rounded-lg shadow border border-gray-200 mb-6">
        <h2 className="text-lg font-semibold mb-4 text-gray-800">연동 테스트</h2>
        <div className="flex gap-4 flex-wrap">
          <button
            onClick={sendTestEvent}
            disabled={loading}
            className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
          >
            {loading ? '전송 중...' : '백엔드 테스트 이벤트 전송'}
          </button>
          <button
            onClick={triggerFrontendError}
            className="px-4 py-2 bg-red-600 text-white text-sm rounded hover:bg-red-700 transition-colors"
          >
            프론트엔드 에러 트리거 (ErrorBoundary 테스트)
          </button>
        </div>
        {testResult && (
          <div className="mt-4 p-3 bg-gray-50 rounded text-sm font-mono text-gray-700">
            {testResult}
          </div>
        )}
      </div>

      {/* 설정 가이드 */}
      <div className="bg-gray-50 p-6 rounded-lg border border-gray-200">
        <h2 className="text-lg font-semibold mb-3 text-gray-800">설정 방법</h2>
        <div className="space-y-2 text-sm text-gray-600">
          <p>1. <a href="https://sentry.io" target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">sentry.io</a>에서 프로젝트 생성 후 DSN을 복사합니다.</p>
          <p>2. 백엔드: <code className="bg-gray-200 px-1 rounded">SENTRY_DSN</code> 환경변수 설정</p>
          <p>3. 프론트엔드: <code className="bg-gray-200 px-1 rounded">NEXT_PUBLIC_SENTRY_DSN</code> 환경변수 설정</p>
          <p>4. 위 테스트 버튼으로 이벤트가 정상 수신되는지 확인합니다.</p>
        </div>
      </div>
    </div>
  );
}
