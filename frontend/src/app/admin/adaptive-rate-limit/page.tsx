'use client';

import { useQuery } from '@tanstack/react-query';

interface CircuitBreakerStatus {
  name: string;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' | 'DISABLED' | 'METRICS_ONLY' | 'FORCED_OPEN';
  policyName: string;
  capacityPerMinute: number;
  failureRate: number;
  numberOfBufferedCalls: number;
  numberOfFailedCalls: number;
  numberOfSuccessfulCalls: number;
}

const STATE_STYLES: Record<string, { badge: string; card: string }> = {
  CLOSED:     { badge: 'bg-green-100 text-green-800',  card: 'border-green-200' },
  OPEN:       { badge: 'bg-red-100 text-red-800',      card: 'border-red-300' },
  HALF_OPEN:  { badge: 'bg-yellow-100 text-yellow-800', card: 'border-yellow-300' },
  DISABLED:   { badge: 'bg-gray-100 text-gray-600',    card: 'border-gray-200' },
  FORCED_OPEN:{ badge: 'bg-orange-100 text-orange-800', card: 'border-orange-300' },
  METRICS_ONLY:{ badge: 'bg-blue-100 text-blue-700',   card: 'border-blue-200' },
};

const POLICY_STYLES: Record<string, string> = {
  Closed:   'text-green-700 font-semibold',
  HalfOpen: 'text-yellow-700 font-semibold',
  Open:     'text-red-700 font-semibold',
};

export default function AdaptiveRateLimitPage() {
  const { data: statuses, isLoading, dataUpdatedAt } = useQuery<CircuitBreakerStatus[]>({
    queryKey: ['adaptive-rate-limit-status'],
    queryFn: () => fetch('/api/adaptive-rate-limit/status').then((r) => r.json()),
    refetchInterval: 3000,
  });

  const total = statuses?.length ?? 0;
  const openCount = statuses?.filter((s) => s.state === 'OPEN').length ?? 0;
  const halfOpenCount = statuses?.filter((s) => s.state === 'HALF_OPEN').length ?? 0;
  const closedCount = statuses?.filter((s) => s.state === 'CLOSED').length ?? 0;

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Circuit Breaker 적응형 Rate Limiting</h1>
        <p className="text-sm text-gray-500 mt-1">
          CB 상태에 따라 Rate Limit이 자동 조정됩니다. (CLOSED: 20/min · HALF_OPEN: 5/min · OPEN: 1/min)
        </p>
        {dataUpdatedAt > 0 && (
          <p className="text-xs text-gray-400 mt-1">
            마지막 갱신: {new Date(dataUpdatedAt).toLocaleTimeString()} (3초 자동 갱신)
          </p>
        )}
      </div>

      {/* 요약 카드 */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        {[
          { label: '전체 CB', value: total, color: 'text-gray-800' },
          { label: 'CLOSED (정상)', value: closedCount, color: 'text-green-600' },
          { label: 'HALF_OPEN (회복 중)', value: halfOpenCount, color: 'text-yellow-600' },
          { label: 'OPEN (차단)', value: openCount, color: 'text-red-600' },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
            <p className="text-xs text-gray-500">{label}</p>
            <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* CB 카드 목록 */}
      {isLoading ? (
        <div className="text-center py-20 text-gray-400">로딩 중...</div>
      ) : !statuses || statuses.length === 0 ? (
        <div className="text-center py-20 text-gray-400">등록된 CircuitBreaker가 없습니다.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {statuses.map((s) => {
            const style = STATE_STYLES[s.state] ?? STATE_STYLES['DISABLED'];
            return (
              <div key={s.name} className={`bg-white border-2 rounded-xl p-5 shadow-sm ${style.card}`}>
                {/* 헤더 */}
                <div className="flex items-center justify-between mb-3">
                  <h2 className="text-base font-bold text-gray-800 font-mono">{s.name}</h2>
                  <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${style.badge}`}>
                    {s.state}
                  </span>
                </div>

                {/* 정책 */}
                <div className="flex items-center gap-2 mb-3 p-2 bg-gray-50 rounded-lg">
                  <span className="text-xs text-gray-500">현재 Rate Limit 정책:</span>
                  <span className={`text-sm ${POLICY_STYLES[s.policyName] ?? 'text-gray-700 font-semibold'}`}>
                    {s.policyName} — {s.capacityPerMinute} req/min
                  </span>
                </div>

                {/* 메트릭 */}
                <div className="grid grid-cols-2 gap-2 text-xs text-gray-600">
                  <div className="flex justify-between bg-gray-50 rounded p-2">
                    <span>실패율</span>
                    <span className={`font-mono font-semibold ${s.failureRate > 50 ? 'text-red-600' : 'text-gray-800'}`}>
                      {s.failureRate === -1 ? 'N/A' : `${s.failureRate.toFixed(1)}%`}
                    </span>
                  </div>
                  <div className="flex justify-between bg-gray-50 rounded p-2">
                    <span>버퍼 호출</span>
                    <span className="font-mono font-semibold">{s.numberOfBufferedCalls}</span>
                  </div>
                  <div className="flex justify-between bg-gray-50 rounded p-2">
                    <span>성공</span>
                    <span className="font-mono font-semibold text-green-600">{s.numberOfSuccessfulCalls}</span>
                  </div>
                  <div className="flex justify-between bg-gray-50 rounded p-2">
                    <span>실패</span>
                    <span className="font-mono font-semibold text-red-600">{s.numberOfFailedCalls}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
