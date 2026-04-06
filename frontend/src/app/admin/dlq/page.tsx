'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface DlqMessage {
  id: number;
  userId: string;
  action: string;
  description: string;
  status: string;
  dlqStatus: 'PENDING' | 'RETRYING' | 'RESOLVED' | 'DISCARDED';
  failedAt: string;
  resolvedAt: string | null;
  retryCount: number;
  lastError: string | null;
  errorMessage: string | null;
}

interface DlqStats {
  total: number;
  pending: number;
  resolved: number;
  discarded: number;
}

interface PageResponse {
  content: DlqMessage[];
  totalElements: number;
  totalPages: number;
  number: number;
}

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  RETRYING: 'bg-blue-100 text-blue-800',
  RESOLVED: 'bg-green-100 text-green-800',
  DISCARDED: 'bg-gray-100 text-gray-700',
};

export default function DlqDashboardPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string>('');

  const { data: stats } = useQuery<DlqStats>({
    queryKey: ['dlq-stats'],
    queryFn: () => fetch('/api/dlq/stats').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const { data: messages, isLoading } = useQuery<PageResponse>({
    queryKey: ['dlq-messages', page, statusFilter],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '20' });
      if (statusFilter) params.set('status', statusFilter);
      return fetch(`/api/dlq?${params}`).then((r) => r.json());
    },
    refetchInterval: 5000,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['dlq-messages'] });
    queryClient.invalidateQueries({ queryKey: ['dlq-stats'] });
  };

  const retryMutation = useMutation({
    mutationFn: (id: number) =>
      fetch(`/api/dlq/${id}/retry`, { method: 'POST' }).then((r) => r.json()),
    onSuccess: invalidate,
  });

  const discardMutation = useMutation({
    mutationFn: (id: number) =>
      fetch(`/api/dlq/${id}/discard`, { method: 'POST' }).then((r) => r.json()),
    onSuccess: invalidate,
  });

  const retryAllMutation = useMutation({
    mutationFn: () =>
      fetch('/api/dlq/retry-all', { method: 'POST' }).then((r) => r.json()),
    onSuccess: invalidate,
  });

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">DLQ 모니터링 대시보드</h1>
        <p className="text-sm text-gray-500 mt-1">처리 실패한 RabbitMQ 메시지를 조회하고 재처리합니다.</p>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-4 gap-4 mb-6">
          {[
            { label: '전체', value: stats.total, color: 'text-gray-800' },
            { label: '대기 (PENDING)', value: stats.pending, color: 'text-yellow-600' },
            { label: '완료 (RESOLVED)', value: stats.resolved, color: 'text-green-600' },
            { label: '폐기 (DISCARDED)', value: stats.discarded, color: 'text-gray-500' },
          ].map(({ label, value, color }) => (
            <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
              <p className="text-xs text-gray-500">{label}</p>
              <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Controls */}
      <div className="flex items-center gap-3 mb-4">
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          className="border rounded px-3 py-2 text-sm"
        >
          <option value="">전체 상태</option>
          <option value="PENDING">PENDING</option>
          <option value="RETRYING">RETRYING</option>
          <option value="RESOLVED">RESOLVED</option>
          <option value="DISCARDED">DISCARDED</option>
        </select>

        <button
          onClick={() => retryAllMutation.mutate()}
          disabled={retryAllMutation.isPending || stats?.pending === 0}
          className="ml-auto px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {retryAllMutation.isPending ? '재처리 중...' : `전체 재처리 (${stats?.pending ?? 0}건)`}
        </button>
      </div>

      {/* Table */}
      <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">ID</th>
              <th className="px-4 py-3 text-left">사용자</th>
              <th className="px-4 py-3 text-left">액션</th>
              <th className="px-4 py-3 text-left">설명</th>
              <th className="px-4 py-3 text-left">상태</th>
              <th className="px-4 py-3 text-left">실패 시각</th>
              <th className="px-4 py-3 text-left">재시도</th>
              <th className="px-4 py-3 text-left">오류</th>
              <th className="px-4 py-3 text-left">작업</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr>
                <td colSpan={9} className="text-center py-10 text-gray-400">로딩 중...</td>
              </tr>
            ) : messages?.content.length === 0 ? (
              <tr>
                <td colSpan={9} className="text-center py-10 text-gray-400">메시지 없음</td>
              </tr>
            ) : (
              messages?.content.map((msg) => (
                <tr key={msg.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs">{msg.id}</td>
                  <td className="px-4 py-3">{msg.userId}</td>
                  <td className="px-4 py-3 font-mono text-xs">{msg.action}</td>
                  <td className="px-4 py-3 max-w-xs truncate" title={msg.description}>{msg.description}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[msg.dlqStatus]}`}>
                      {msg.dlqStatus}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{msg.failedAt.replace('T', ' ').slice(0, 19)}</td>
                  <td className="px-4 py-3 text-center">{msg.retryCount}</td>
                  <td className="px-4 py-3 max-w-xs truncate text-red-500 text-xs" title={msg.lastError ?? ''}>
                    {msg.lastError ?? msg.errorMessage ?? '-'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      {msg.dlqStatus === 'PENDING' && (
                        <>
                          <button
                            onClick={() => retryMutation.mutate(msg.id)}
                            disabled={retryMutation.isPending}
                            className="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs hover:bg-blue-100"
                          >
                            재처리
                          </button>
                          <button
                            onClick={() => discardMutation.mutate(msg.id)}
                            disabled={discardMutation.isPending}
                            className="px-2 py-1 bg-red-50 text-red-700 rounded text-xs hover:bg-red-100"
                          >
                            폐기
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>

        {/* Pagination */}
        {messages && messages.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t text-sm">
            <span className="text-gray-500">
              총 {messages.totalElements}건 / {messages.totalPages}페이지
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1 border rounded disabled:opacity-40"
              >
                이전
              </button>
              <span className="px-3 py-1">{page + 1}</span>
              <button
                onClick={() => setPage((p) => Math.min(messages.totalPages - 1, p + 1))}
                disabled={page >= messages.totalPages - 1}
                className="px-3 py-1 border rounded disabled:opacity-40"
              >
                다음
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
