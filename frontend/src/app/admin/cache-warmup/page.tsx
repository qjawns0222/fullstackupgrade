'use client';

import { useState, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';

interface WarmupStepResult {
  cacheName: string;
  loaded: number;
  error: string | null;
}

interface WarmupResult {
  status: 'IDLE' | 'RUNNING' | 'DONE';
  steps: WarmupStepResult[];
  totalLoaded: number;
}

export default function CacheWarmupPage() {
  const [logs, setLogs] = useState<string[]>([]);
  const [running, setRunning] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);

  const { data: status, refetch } = useQuery<WarmupResult>({
    queryKey: ['cache-warmup-status'],
    queryFn: () => fetch('/api/cache-warmup/status').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const handleTrigger = () => {
    if (running) return;
    setLogs([]);
    setRunning(true);

    const es = new EventSource('/api/cache-warmup/trigger');
    eventSourceRef.current = es;

    es.addEventListener('progress', (e) => {
      setLogs((prev) => [...prev, `[진행] ${e.data}`]);
    });

    es.addEventListener('done', (e) => {
      setLogs((prev) => [...prev, `[완료] ${e.data}`]);
      es.close();
      setRunning(false);
      refetch();
    });

    es.onerror = () => {
      setLogs((prev) => [...prev, '[오류] SSE 연결 끊김']);
      es.close();
      setRunning(false);
      refetch();
    };
  };

  const statusColor = {
    IDLE: 'text-gray-500',
    RUNNING: 'text-blue-600',
    DONE: 'text-green-600',
  }[status?.status ?? 'IDLE'];

  const statusLabel = {
    IDLE: '대기',
    RUNNING: '워밍 중',
    DONE: '완료',
  }[status?.status ?? 'IDLE'];

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">캐시 워밍 대시보드</h1>
        <p className="text-sm text-gray-500 mt-1">
          콜드 스타트 시 비어있는 2레벨 캐시(Caffeine+Redis)를 미리 채워 초기 DB 풀히트를 방지합니다.
        </p>
      </div>

      {/* 상태 카드 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">워밍 상태</p>
          <p className={`text-2xl font-bold mt-1 ${statusColor}`}>{statusLabel}</p>
        </div>
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">총 로드 항목</p>
          <p className="text-2xl font-bold mt-1 text-gray-800">{status?.totalLoaded ?? 0}</p>
        </div>
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">캐시 영역 수</p>
          <p className="text-2xl font-bold mt-1 text-gray-800">{status?.steps.length ?? 0}</p>
        </div>
      </div>

      {/* 트리거 버튼 */}
      <div className="mb-6">
        <button
          onClick={handleTrigger}
          disabled={running}
          className="px-5 py-2 bg-blue-600 text-white text-sm font-medium rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {running ? '워밍 진행 중...' : '수동 캐시 워밍 실행'}
        </button>
        <span className="ml-3 text-xs text-gray-400">
          SSE 스트림으로 진행상황을 실시간으로 표시합니다.
        </span>
      </div>

      {/* 스텝별 결과 */}
      {status && status.steps.length > 0 && (
        <div className="mb-6">
          <h2 className="text-sm font-semibold text-gray-700 mb-2">캐시 영역별 결과</h2>
          <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">캐시 영역</th>
                  <th className="px-4 py-3 text-left">로드 수</th>
                  <th className="px-4 py-3 text-left">상태</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {status.steps.map((step) => (
                  <tr key={step.cacheName} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs">{step.cacheName}</td>
                    <td className="px-4 py-3">{step.loaded}</td>
                    <td className="px-4 py-3">
                      {step.error ? (
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                          오류: {step.error}
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700">
                          성공
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* SSE 로그 */}
      {logs.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-gray-700 mb-2">실시간 진행 로그</h2>
          <div className="bg-gray-900 rounded-lg p-4 font-mono text-xs text-green-400 space-y-1 max-h-60 overflow-y-auto">
            {logs.map((log, i) => (
              <div key={i}>{log}</div>
            ))}
            {running && <div className="animate-pulse">▌</div>}
          </div>
        </div>
      )}
    </div>
  );
}
