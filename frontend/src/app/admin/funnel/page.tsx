'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

interface FunnelStep {
  eventType: string;
  sessionCount: number;
  conversionRate: number;
}

interface FunnelStats {
  steps: FunnelStep[];
  totalSessions: number;
  periodHours: number;
}

const EVENT_LABELS: Record<string, string> = {
  RESUME_VIEW: '이력서 조회',
  RESUME_SAVE: '이력서 저장',
  RESUME_DOWNLOAD: '이력서 다운로드',
};

const STEP_COLORS = [
  'bg-blue-500',
  'bg-indigo-500',
  'bg-purple-500',
];

export default function FunnelDashboardPage() {
  const [periodHours, setPeriodHours] = useState(24);

  const { data: stats, isLoading } = useQuery<FunnelStats>({
    queryKey: ['funnel-stats', periodHours],
    queryFn: () =>
      fetch(`/api/funnel/stats?periodHours=${periodHours}`).then((r) => r.json()),
    refetchInterval: 5000,
  });

  const maxCount = stats?.steps.reduce((m, s) => Math.max(m, s.sessionCount), 1) ?? 1;

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">퍼널 분석 대시보드</h1>
        <p className="text-sm text-gray-500 mt-1">
          사용자 행동 흐름(조회 → 저장 → 다운로드)의 전환율을 분석합니다.
        </p>
      </div>

      {/* 기간 선택 */}
      <div className="flex items-center gap-3 mb-6">
        <span className="text-sm text-gray-600 font-medium">분석 기간:</span>
        {[6, 24, 48, 168].map((h) => (
          <button
            key={h}
            onClick={() => setPeriodHours(h)}
            className={`px-3 py-1.5 text-sm rounded-full border transition-colors ${
              periodHours === h
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-300 hover:border-blue-400'
            }`}
          >
            {h < 24 ? `${h}시간` : h === 24 ? '24시간' : h === 48 ? '2일' : '7일'}
          </button>
        ))}
      </div>

      {/* 요약 카드 */}
      {stats && (
        <div className="grid grid-cols-3 gap-4 mb-8">
          {stats.steps.map((step, i) => (
            <div key={step.eventType} className="bg-white border rounded-lg p-4 shadow-sm">
              <p className="text-xs text-gray-500">{EVENT_LABELS[step.eventType] ?? step.eventType}</p>
              <p className="text-3xl font-bold text-gray-800 mt-1">{step.sessionCount.toLocaleString()}</p>
              <p className={`text-sm mt-1 font-medium ${i === 0 ? 'text-blue-600' : 'text-purple-600'}`}>
                {i === 0 ? '기준 (100%)' : `전환율 ${step.conversionRate}%`}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* 퍼널 바 차트 */}
      <div className="bg-white border rounded-lg p-6 shadow-sm">
        <h2 className="text-base font-semibold text-gray-800 mb-5">전환 퍼널</h2>
        {isLoading ? (
          <div className="text-center py-10 text-gray-400">로딩 중...</div>
        ) : stats?.steps.length === 0 ? (
          <div className="text-center py-10 text-gray-400">데이터 없음</div>
        ) : (
          <div className="space-y-5">
            {stats?.steps.map((step, i) => {
              const widthPct = maxCount === 0 ? 0 : (step.sessionCount / maxCount) * 100;
              return (
                <div key={step.eventType}>
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center gap-2">
                      <span className="w-6 h-6 rounded-full bg-gray-100 text-gray-600 text-xs flex items-center justify-center font-bold">
                        {i + 1}
                      </span>
                      <span className="text-sm font-medium text-gray-700">
                        {EVENT_LABELS[step.eventType] ?? step.eventType}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-sm">
                      <span className="text-gray-500">{step.sessionCount.toLocaleString()}세션</span>
                      <span className={`font-semibold ${i === 0 ? 'text-blue-600' : 'text-purple-600'}`}>
                        {step.conversionRate}%
                      </span>
                    </div>
                  </div>
                  <div className="h-8 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className={`h-full ${STEP_COLORS[i] ?? 'bg-gray-400'} rounded-full transition-all duration-500`}
                      style={{ width: `${widthPct}%` }}
                    />
                  </div>
                  {i < (stats.steps.length - 1) && (
                    <div className="flex items-center gap-2 mt-2 pl-8">
                      <svg className="w-3 h-3 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                      <span className="text-xs text-gray-400">
                        이탈: {(step.sessionCount - (stats.steps[i + 1]?.sessionCount ?? 0)).toLocaleString()}세션
                      </span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-4 text-right">5초마다 자동 갱신 · 기간: {periodHours}시간</p>
    </div>
  );
}
