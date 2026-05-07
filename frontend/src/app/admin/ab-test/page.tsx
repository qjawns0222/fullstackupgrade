'use client';

import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';

interface AbTestResult {
  id: number;
  toggleName: string;
  variantName: string;
  userId: string | null;
  sessionId: string | null;
  payload: string | null;
  recordedAt: string;
}

interface VariantStats {
  toggleName: string;
  variants: Record<string, number>;
  total: number;
  periodHours: number;
}

const VARIANT_COLORS: Record<string, string> = {
  disabled: 'bg-gray-100 text-gray-600',
  A: 'bg-blue-100 text-blue-800',
  B: 'bg-green-100 text-green-800',
  C: 'bg-purple-100 text-purple-800',
  control: 'bg-yellow-100 text-yellow-800',
};

function variantColor(name: string): string {
  return VARIANT_COLORS[name] ?? 'bg-orange-100 text-orange-800';
}

export default function AbTestPage() {
  const [toggleName, setToggleName] = useState('checkout-flow');
  const [userId, setUserId] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [periodHours, setPeriodHours] = useState(24);
  const [activeToggle, setActiveToggle] = useState('checkout-flow');

  const { data: stats, refetch: refetchStats } = useQuery<VariantStats>({
    queryKey: ['ab-stats', activeToggle, periodHours],
    queryFn: () =>
      fetch(`/api/ab-test/stats/${activeToggle}?periodHours=${periodHours}`).then((r) => r.json()),
    refetchInterval: 5000,
  });

  const { data: results, refetch: refetchResults } = useQuery<AbTestResult[]>({
    queryKey: ['ab-results', activeToggle],
    queryFn: () =>
      fetch(`/api/ab-test/results/${activeToggle}?limit=20`).then((r) => r.json()),
    refetchInterval: 5000,
  });

  const assignMutation = useMutation({
    mutationFn: () =>
      fetch('/api/ab-test/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          toggleName,
          userId: userId || null,
          sessionId: sessionId || null,
        }),
      }).then((r) => r.json()),
    onSuccess: () => {
      setActiveToggle(toggleName);
      refetchStats();
      refetchResults();
    },
  });

  const totalVariants = stats ? Object.values(stats.variants).reduce((a, b) => a + b, 0) : 0;

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">A/B 테스트 대시보드</h1>
        <p className="text-sm text-gray-500 mt-1">
          Unleash Variant API 기반 A/B 테스트 — variant 배정 및 퍼널 분석
        </p>
      </div>

      {/* Assign Variant */}
      <div className="bg-white border rounded-lg p-5 shadow-sm mb-6">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">Variant 배정 테스트</h2>
        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-xs text-gray-500 mb-1">Toggle 이름</label>
            <input
              value={toggleName}
              onChange={(e) => setToggleName(e.target.value)}
              className="border rounded px-3 py-2 text-sm w-44"
              placeholder="checkout-flow"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">User ID (선택)</label>
            <input
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="border rounded px-3 py-2 text-sm w-36"
              placeholder="user-123"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">Session ID (선택)</label>
            <input
              value={sessionId}
              onChange={(e) => setSessionId(e.target.value)}
              className="border rounded px-3 py-2 text-sm w-36"
              placeholder="sess-abc"
            />
          </div>
          <button
            onClick={() => assignMutation.mutate()}
            disabled={assignMutation.isPending || !toggleName}
            className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {assignMutation.isPending ? '배정 중...' : 'Variant 배정'}
          </button>
        </div>
        {assignMutation.data && (
          <div className="mt-3 p-3 bg-gray-50 rounded text-sm">
            <span className="text-gray-600">결과: </span>
            <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${variantColor(assignMutation.data.variantName)}`}>
              {assignMutation.data.variantName}
            </span>
            {assignMutation.data.payload && (
              <span className="ml-2 text-gray-500 font-mono text-xs">{assignMutation.data.payload}</span>
            )}
          </div>
        )}
      </div>

      {/* Stats Filter */}
      <div className="flex items-center gap-3 mb-4">
        <input
          value={activeToggle}
          onChange={(e) => setActiveToggle(e.target.value)}
          className="border rounded px-3 py-2 text-sm w-44"
          placeholder="Toggle 이름"
        />
        <select
          value={periodHours}
          onChange={(e) => setPeriodHours(Number(e.target.value))}
          className="border rounded px-3 py-2 text-sm"
        >
          <option value={1}>최근 1시간</option>
          <option value={6}>최근 6시간</option>
          <option value={24}>최근 24시간</option>
          <option value={168}>최근 7일</option>
        </select>
      </div>

      {/* Variant Stats */}
      {stats && (
        <div className="bg-white border rounded-lg p-5 shadow-sm mb-6">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">
            Variant 분포 — <span className="font-mono">{stats.toggleName}</span>
            <span className="ml-2 text-gray-400 font-normal">총 {stats.total}건</span>
          </h2>
          {Object.keys(stats.variants).length === 0 ? (
            <p className="text-gray-400 text-sm">데이터 없음</p>
          ) : (
            <div className="space-y-3">
              {Object.entries(stats.variants).map(([name, count]) => {
                const pct = totalVariants > 0 ? Math.round((count / totalVariants) * 100) : 0;
                return (
                  <div key={name}>
                    <div className="flex items-center justify-between mb-1">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${variantColor(name)}`}>{name}</span>
                      <span className="text-sm text-gray-600">{count}건 ({pct}%)</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div
                        className="bg-blue-500 h-2 rounded-full transition-all"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Recent Results */}
      <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
        <div className="px-5 py-3 border-b bg-gray-50">
          <h2 className="text-sm font-semibold text-gray-700">최근 배정 이력</h2>
        </div>
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">ID</th>
              <th className="px-4 py-3 text-left">Toggle</th>
              <th className="px-4 py-3 text-left">Variant</th>
              <th className="px-4 py-3 text-left">User</th>
              <th className="px-4 py-3 text-left">Session</th>
              <th className="px-4 py-3 text-left">Payload</th>
              <th className="px-4 py-3 text-left">시각</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {!results || results.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-10 text-gray-400">데이터 없음</td>
              </tr>
            ) : (
              results.map((r) => (
                <tr key={r.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-400">{r.id}</td>
                  <td className="px-4 py-3 font-mono text-xs">{r.toggleName}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${variantColor(r.variantName)}`}>
                      {r.variantName}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{r.userId ?? '-'}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{r.sessionId ?? '-'}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500 max-w-xs truncate">
                    {r.payload ?? '-'}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-400">
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
