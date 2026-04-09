'use client';

import { useQuery } from '@tanstack/react-query';

interface MigrationInfo {
  version: string;
  description: string;
  type: string;
  state: string;
  installedOn: string | null;
  executionTime: number | null;
}

interface MigrationStatusResponse {
  total: number;
  applied: number;
  failed: number;
  pending: number;
  currentVersion: string;
  migrations: MigrationInfo[];
}

const STATE_COLORS: Record<string, string> = {
  SUCCESS: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  PENDING: 'bg-yellow-100 text-yellow-800',
  ABOVE_TARGET: 'bg-gray-100 text-gray-600',
  BASELINE: 'bg-blue-100 text-blue-800',
  MISSING_SUCCESS: 'bg-orange-100 text-orange-800',
};

export default function MigrationDashboardPage() {
  const { data, isLoading, error, refetch } = useQuery<MigrationStatusResponse>({
    queryKey: ['migration-status'],
    queryFn: () => fetch('/api/migration/status').then((r) => r.json()),
    refetchInterval: 5000,
  });

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">DB 마이그레이션 상태</h1>
          <p className="text-sm text-gray-500 mt-1">
            Flyway 마이그레이션 이력 및 현재 스키마 버전을 확인합니다.
          </p>
        </div>
        <button
          onClick={() => refetch()}
          className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
        >
          새로고침
        </button>
      </div>

      {isLoading && (
        <div className="text-center py-20 text-gray-400">로딩 중...</div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          백엔드 연결 실패 — 서버가 실행 중인지 확인하세요.
        </div>
      )}

      {data && (
        <>
          {/* Stats */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-6">
            {[
              { label: '현재 버전', value: data.currentVersion, color: 'text-blue-600' },
              { label: '전체', value: data.total, color: 'text-gray-800' },
              { label: '적용 완료', value: data.applied, color: 'text-green-600' },
              { label: '실패', value: data.failed, color: 'text-red-600' },
              { label: '대기', value: data.pending, color: 'text-yellow-600' },
            ].map(({ label, value, color }) => (
              <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
                <p className="text-xs text-gray-500">{label}</p>
                <p className={`text-2xl font-bold mt-1 ${color}`}>{value}</p>
              </div>
            ))}
          </div>

          {/* Failed alert */}
          {data.failed > 0 && (
            <div className="mb-4 bg-red-50 border border-red-300 rounded-lg p-4 text-red-700 text-sm font-medium">
              ⚠️ 실패한 마이그레이션이 {data.failed}건 있습니다. 즉시 확인이 필요합니다.
            </div>
          )}

          {/* Migration table */}
          <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">버전</th>
                  <th className="px-4 py-3 text-left">설명</th>
                  <th className="px-4 py-3 text-left">타입</th>
                  <th className="px-4 py-3 text-left">상태</th>
                  <th className="px-4 py-3 text-left">적용 시각</th>
                  <th className="px-4 py-3 text-right">실행 시간(ms)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data.migrations.map((m) => (
                  <tr key={`${m.version}-${m.description}`} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-gray-700">
                      {m.version}
                    </td>
                    <td className="px-4 py-3 text-gray-700">{m.description}</td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">{m.type}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                          STATE_COLORS[m.state] ?? 'bg-gray-100 text-gray-600'
                        }`}
                      >
                        {m.state}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {m.installedOn ?? '-'}
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-xs text-gray-500">
                      {m.executionTime ?? '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
