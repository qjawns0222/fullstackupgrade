'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

interface SpanRecord {
  id: number;
  spanName: string;
  className: string;
  methodName: string;
  durationMs: number;
  status: 'SUCCESS' | 'SLOW' | 'ERROR';
  errorMessage: string | null;
  recordedAt: string;
}

interface SpanStats {
  totalCount: number;
  slowCount: number;
  errorCount: number;
  avgDurationMs: number;
}

const STATUS_COLORS: Record<string, string> = {
  SUCCESS: 'bg-green-100 text-green-800',
  SLOW: 'bg-yellow-100 text-yellow-800',
  ERROR: 'bg-red-100 text-red-800',
};

export default function TracingDashboardPage() {
  const [tab, setTab] = useState<'recent' | 'slow'>('recent');
  const [thresholdMs, setThresholdMs] = useState(500);

  const { data: stats } = useQuery<SpanStats>({
    queryKey: ['tracing-stats'],
    queryFn: () => fetch('/api/tracing/stats').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const { data: records, isLoading } = useQuery<SpanRecord[]>({
    queryKey: ['tracing-records', tab, thresholdMs],
    queryFn: () => {
      const url =
        tab === 'recent'
          ? '/api/tracing/recent?limit=50'
          : `/api/tracing/slow?thresholdMs=${thresholdMs}&limit=50`;
      return fetch(url).then((r) => r.json());
    },
    refetchInterval: 5000,
  });

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">메서드 레벨 Tracing 대시보드</h1>
        <p className="text-sm text-gray-500 mt-1">
          @WithSpan 어노테이션이 붙은 메서드의 실행 시간을 추적합니다.
        </p>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-4 gap-4 mb-6">
          {[
            { label: '전체 Span', value: stats.totalCount, color: 'text-gray-800' },
            { label: 'SLOW (병목)', value: stats.slowCount, color: 'text-yellow-600' },
            { label: 'ERROR', value: stats.errorCount, color: 'text-red-600' },
            {
              label: '평균 응답(ms)',
              value: stats.avgDurationMs.toFixed(1),
              color: 'text-blue-600',
            },
          ].map(({ label, value, color }) => (
            <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
              <p className="text-xs text-gray-500">{label}</p>
              <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Tabs & Filter */}
      <div className="flex items-center gap-4 mb-4">
        <div className="flex border rounded overflow-hidden text-sm">
          {(['recent', 'slow'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-4 py-2 ${tab === t ? 'bg-blue-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}
            >
              {t === 'recent' ? '최근 Span' : 'SLOW Span'}
            </button>
          ))}
        </div>

        {tab === 'slow' && (
          <div className="flex items-center gap-2 text-sm">
            <label className="text-gray-600">임계값(ms)</label>
            <input
              type="number"
              value={thresholdMs}
              onChange={(e) => setThresholdMs(Number(e.target.value))}
              className="border rounded px-2 py-1 w-24"
              min={1}
            />
          </div>
        )}
      </div>

      {/* Table */}
      <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">Span 이름</th>
              <th className="px-4 py-3 text-left">클래스</th>
              <th className="px-4 py-3 text-left">메서드</th>
              <th className="px-4 py-3 text-right">응답시간(ms)</th>
              <th className="px-4 py-3 text-left">상태</th>
              <th className="px-4 py-3 text-left">에러</th>
              <th className="px-4 py-3 text-left">기록 시각</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr>
                <td colSpan={7} className="text-center py-10 text-gray-400">
                  로딩 중...
                </td>
              </tr>
            ) : !records?.length ? (
              <tr>
                <td colSpan={7} className="text-center py-10 text-gray-400">
                  데이터 없음
                </td>
              </tr>
            ) : (
              records.map((r) => (
                <tr key={r.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs font-semibold">{r.spanName}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{r.className}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{r.methodName}</td>
                  <td className="px-4 py-3 text-right font-mono font-bold">
                    <span className={r.durationMs >= thresholdMs ? 'text-yellow-600' : 'text-gray-800'}>
                      {r.durationMs}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[r.status]}`}
                    >
                      {r.status}
                    </span>
                  </td>
                  <td
                    className="px-4 py-3 max-w-xs truncate text-red-500 text-xs"
                    title={r.errorMessage ?? ''}
                  >
                    {r.errorMessage ?? '-'}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {r.recordedAt.replace('T', ' ').slice(0, 19)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
