'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface QueryHintEntry {
  normalizedSql: string;
  hint: string;
  slowCount: number;
  registeredAt: string;
}

interface QueryHintSummary {
  totalRegistered: number;
  entries: QueryHintEntry[];
}

const HINT_COLORS: Record<string, string> = {
  'NO_FILESORT': 'bg-purple-100 text-purple-800',
  'USE_INDEX_MERGE': 'bg-blue-100 text-blue-800',
  'MAX_EXECUTION_TIME': 'bg-orange-100 text-orange-800',
};

function hintBadgeClass(hint: string): string {
  for (const [key, cls] of Object.entries(HINT_COLORS)) {
    if (hint.includes(key)) return cls;
  }
  return 'bg-gray-100 text-gray-700';
}

export default function QueryHintPage() {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery<QueryHintSummary>({
    queryKey: ['query-hints'],
    queryFn: () => fetch('/api/query-hints').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['query-hints'] });

  const removeMutation = useMutation({
    mutationFn: (sql: string) =>
      fetch(`/api/query-hints?sql=${encodeURIComponent(sql)}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });

  const clearMutation = useMutation({
    mutationFn: () => fetch('/api/query-hints/all', { method: 'DELETE' }),
    onSuccess: invalidate,
  });

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">쿼리 힌트 레지스트리</h1>
        <p className="text-sm text-gray-500 mt-1">
          반복 슬로우 쿼리에 자동 등록된 Optimizer 힌트 목록입니다. (5초 폴링)
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">등록된 힌트 수</p>
          <p className="text-3xl font-bold mt-1 text-indigo-600">
            {data?.totalRegistered ?? '-'}
          </p>
        </div>
        <div className="bg-white border rounded-lg p-4 shadow-sm flex items-center justify-end">
          <button
            onClick={() => clearMutation.mutate()}
            disabled={clearMutation.isPending || !data?.totalRegistered}
            className="px-4 py-2 bg-red-600 text-white text-sm rounded hover:bg-red-700 disabled:opacity-50"
          >
            {clearMutation.isPending ? '초기화 중...' : '전체 초기화'}
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">힌트</th>
              <th className="px-4 py-3 text-left">슬로우 횟수</th>
              <th className="px-4 py-3 text-left">등록 시각</th>
              <th className="px-4 py-3 text-left">SQL 패턴</th>
              <th className="px-4 py-3 text-left">작업</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr>
                <td colSpan={5} className="text-center py-10 text-gray-400">로딩 중...</td>
              </tr>
            ) : !data?.entries.length ? (
              <tr>
                <td colSpan={5} className="text-center py-10 text-gray-400">
                  등록된 힌트 없음 — 슬로우 쿼리가 임계 횟수에 도달하면 자동 등록됩니다.
                </td>
              </tr>
            ) : (
              data.entries.map((entry) => (
                <tr key={entry.normalizedSql} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-mono font-medium ${hintBadgeClass(entry.hint)}`}>
                      {entry.hint}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center font-bold text-red-600">
                    {entry.slowCount}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {entry.registeredAt.replace('T', ' ').slice(0, 19)}
                  </td>
                  <td className="px-4 py-3 max-w-md">
                    <code className="text-xs text-gray-700 bg-gray-100 px-2 py-1 rounded block truncate" title={entry.normalizedSql}>
                      {entry.normalizedSql}
                    </code>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => removeMutation.mutate(entry.normalizedSql)}
                      disabled={removeMutation.isPending}
                      className="px-2 py-1 bg-red-50 text-red-700 rounded text-xs hover:bg-red-100 disabled:opacity-50"
                    >
                      제거
                    </button>
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
