'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

interface ChangeStats {
  totalBreakingChanges: number;
  byType: Record<string, number>;
  latestSnapshot: string | null;
}

interface BreakingChange {
  id: number;
  oldVersion: string;
  newVersion: string;
  changeType: string;
  description: string;
  element: string | null;
  detectedAt: string;
}

interface Snapshot {
  id: number;
  version: string;
  createdAt: string;
}

const CHANGE_TYPE_COLORS: Record<string, string> = {
  ENDPOINT_REMOVED: 'bg-red-100 text-red-800',
  PARAMETER_ADDED_REQUIRED: 'bg-orange-100 text-orange-800',
  PARAMETER_REMOVED: 'bg-yellow-100 text-yellow-800',
  RESPONSE_SCHEMA_CHANGED: 'bg-purple-100 text-purple-800',
  REQUEST_BODY_CHANGED: 'bg-blue-100 text-blue-800',
};

const CHANGE_TYPE_LABELS: Record<string, string> = {
  ENDPOINT_REMOVED: '엔드포인트 삭제',
  PARAMETER_ADDED_REQUIRED: '필수 파라미터 추가',
  PARAMETER_REMOVED: '파라미터 삭제',
  RESPONSE_SCHEMA_CHANGED: '응답 스키마 변경',
  REQUEST_BODY_CHANGED: '요청 바디 변경',
};

export default function ApiChangesPage() {
  const [tab, setTab] = useState<'breaking' | 'snapshots'>('breaking');

  const { data: stats } = useQuery<ChangeStats>({
    queryKey: ['api-change-stats'],
    queryFn: () => fetch('http://localhost:8080/api/api-changes/stats').then((r) => r.json()),
    refetchInterval: 30000,
  });

  const { data: breakingChanges = [], isLoading: loadingChanges } = useQuery<BreakingChange[]>({
    queryKey: ['api-breaking-changes'],
    queryFn: () => fetch('http://localhost:8080/api/api-changes/breaking').then((r) => r.json()),
    refetchInterval: 30000,
  });

  const { data: snapshots = [], isLoading: loadingSnapshots } = useQuery<Snapshot[]>({
    queryKey: ['api-snapshots'],
    queryFn: () => fetch('http://localhost:8080/api/api-changes/snapshots').then((r) => r.json()),
    refetchInterval: 30000,
  });

  const hasBreaking = breakingChanges.length > 0;

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">API Breaking Change 감지</h1>
        <p className="text-sm text-gray-500 mt-1">
          앱 시작 시 OpenAPI 스펙을 자동 비교하여 하위 호환성 위반을 감지합니다.
        </p>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <StatCard
          label="전체 Breaking Changes"
          value={stats?.totalBreakingChanges ?? '-'}
          color={hasBreaking ? 'text-red-600' : 'text-green-600'}
        />
        <StatCard
          label="최신 스냅샷 버전"
          value={stats?.latestSnapshot ?? '없음'}
          color="text-blue-600"
        />
        <StatCard
          label="저장된 스냅샷"
          value={snapshots.length}
          color="text-gray-700"
        />
      </div>

      {/* 변경 유형별 분포 */}
      {stats && Object.keys(stats.byType).length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4 mb-6">
          <h2 className="text-sm font-semibold text-gray-700 mb-3">변경 유형별 현황</h2>
          <div className="flex flex-wrap gap-2">
            {Object.entries(stats.byType).map(([type, count]) => (
              <span
                key={type}
                className={`px-3 py-1 rounded-full text-xs font-medium ${CHANGE_TYPE_COLORS[type] ?? 'bg-gray-100 text-gray-700'}`}
              >
                {CHANGE_TYPE_LABELS[type] ?? type}: {count}건
              </span>
            ))}
          </div>
        </div>
      )}

      {/* 호환성 배너 */}
      <div
        className={`rounded-lg p-3 mb-6 flex items-center gap-2 text-sm font-medium ${
          hasBreaking
            ? 'bg-red-50 text-red-700 border border-red-200'
            : 'bg-green-50 text-green-700 border border-green-200'
        }`}
      >
        <span>{hasBreaking ? '⚠️' : '✅'}</span>
        <span>
          {hasBreaking
            ? `${breakingChanges.length}건의 Breaking Change가 감지되었습니다. 클라이언트 호환성을 확인하세요.`
            : '현재까지 감지된 Breaking Change가 없습니다.'}
        </span>
      </div>

      {/* 탭 */}
      <div className="flex gap-1 mb-4 border-b border-gray-200">
        {(['breaking', 'snapshots'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === t
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'breaking' ? `Breaking Changes (${breakingChanges.length})` : `스냅샷 이력 (${snapshots.length})`}
          </button>
        ))}
      </div>

      {/* Breaking Changes 탭 */}
      {tab === 'breaking' && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          {loadingChanges ? (
            <div className="p-8 text-center text-gray-400 text-sm">불러오는 중...</div>
          ) : breakingChanges.length === 0 ? (
            <div className="p-8 text-center text-gray-400 text-sm">감지된 Breaking Change가 없습니다.</div>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">유형</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">설명</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">버전</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">감지 시각</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {breakingChanges.map((bc) => (
                  <tr key={bc.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-0.5 rounded text-xs font-medium ${
                          CHANGE_TYPE_COLORS[bc.changeType] ?? 'bg-gray-100 text-gray-700'
                        }`}
                      >
                        {CHANGE_TYPE_LABELS[bc.changeType] ?? bc.changeType}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-800 max-w-sm">
                      <div>{bc.description}</div>
                      {bc.element && (
                        <div className="text-xs text-gray-400 font-mono mt-0.5">{bc.element}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-500 font-mono text-xs">
                      {bc.oldVersion} → {bc.newVersion}
                    </td>
                    <td className="px-4 py-3 text-gray-400 text-xs">
                      {new Date(bc.detectedAt).toLocaleString('ko-KR')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* 스냅샷 이력 탭 */}
      {tab === 'snapshots' && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          {loadingSnapshots ? (
            <div className="p-8 text-center text-gray-400 text-sm">불러오는 중...</div>
          ) : snapshots.length === 0 ? (
            <div className="p-8 text-center text-gray-400 text-sm">저장된 스냅샷이 없습니다.</div>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">#</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">버전</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">저장 시각</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {snapshots.map((snap, idx) => (
                  <tr key={snap.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-400 text-xs">{idx + 1}</td>
                    <td className="px-4 py-3 font-mono text-blue-600">{snap.version}</td>
                    <td className="px-4 py-3 text-gray-400 text-xs">
                      {new Date(snap.createdAt).toLocaleString('ko-KR')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  color,
}: {
  label: string;
  value: string | number;
  color: string;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <div className="text-xs text-gray-500 mb-1">{label}</div>
      <div className={`text-2xl font-bold ${color}`}>{value}</div>
    </div>
  );
}
