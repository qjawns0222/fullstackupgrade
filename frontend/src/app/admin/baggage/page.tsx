'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface BaggageContext {
  userId: string | null;
  tenantId: string | null;
}

interface SnapshotEntry {
  key: string;
  value: string;
}

export default function BaggagePropagationPage() {
  const queryClient = useQueryClient();
  const [userId, setUserId] = useState('');
  const [tenantId, setTenantId] = useState('');

  const { data: current, isLoading } = useQuery<BaggageContext>({
    queryKey: ['baggage-current'],
    queryFn: () => fetch('/api/baggage/current').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const { data: snapshot } = useQuery<Record<string, string>>({
    queryKey: ['baggage-snapshot'],
    queryFn: () => fetch('/api/baggage/snapshot').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const setMutation = useMutation({
    mutationFn: (body: { userId: string | null; tenantId: string | null }) =>
      fetch('/api/baggage/set', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      }).then((r) => r.json()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['baggage-current'] });
      queryClient.invalidateQueries({ queryKey: ['baggage-snapshot'] });
    },
  });

  const snapshotEntries: SnapshotEntry[] = snapshot
    ? Object.entries(snapshot).map(([key, value]) => ({ key, value }))
    : [];

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">분산 추적 Baggage 전파</h1>
        <p className="text-sm text-gray-500 mt-1">
          userId / tenantId를 W3C Baggage 헤더로 RabbitMQ + @Async 경계에 전파합니다.
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        {[
          {
            label: 'userId',
            value: current?.userId ?? '없음',
            color: current?.userId ? 'text-blue-600' : 'text-gray-400',
          },
          {
            label: 'tenantId',
            value: current?.tenantId ?? '없음',
            color: current?.tenantId ? 'text-green-600' : 'text-gray-400',
          },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
            <p className="text-xs text-gray-500">{label}</p>
            <p className={`text-xl font-bold mt-1 font-mono ${color}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* Set Baggage Form */}
      <div className="bg-white border rounded-lg p-5 shadow-sm mb-6">
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Baggage 설정 (현재 트레이스)</h2>
        <div className="flex gap-3 items-end">
          <div>
            <label className="text-xs text-gray-500 block mb-1">userId</label>
            <input
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="예: user-42"
              className="border rounded px-3 py-1.5 text-sm w-40"
            />
          </div>
          <div>
            <label className="text-xs text-gray-500 block mb-1">tenantId</label>
            <input
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              placeholder="예: tenant-A"
              className="border rounded px-3 py-1.5 text-sm w-40"
            />
          </div>
          <button
            onClick={() =>
              setMutation.mutate({
                userId: userId || null,
                tenantId: tenantId || null,
              })
            }
            disabled={setMutation.isPending}
            className="px-4 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {setMutation.isPending ? '설정 중...' : 'Baggage 설정'}
          </button>
        </div>
      </div>

      {/* Snapshot Table */}
      <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b bg-gray-50">
          <h2 className="text-sm font-semibold text-gray-700">현재 Baggage 스냅샷</h2>
          <p className="text-xs text-gray-400 mt-0.5">RabbitMQ 메시지 헤더 및 @Async 스레드에 전파되는 값</p>
        </div>
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">키</th>
              <th className="px-4 py-3 text-left">값</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr>
                <td colSpan={2} className="text-center py-8 text-gray-400">로딩 중...</td>
              </tr>
            ) : snapshotEntries.length === 0 ? (
              <tr>
                <td colSpan={2} className="text-center py-8 text-gray-400">
                  설정된 Baggage 없음
                </td>
              </tr>
            ) : (
              snapshotEntries.map(({ key, value }) => (
                <tr key={key} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs font-semibold text-blue-700">{key}</td>
                  <td className="px-4 py-3 font-mono text-xs">{value}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Propagation Info */}
      <div className="mt-6 bg-blue-50 border border-blue-100 rounded-lg p-4 text-sm text-blue-800">
        <p className="font-semibold mb-1">전파 경로</p>
        <ul className="list-disc list-inside space-y-1 text-xs">
          <li><strong>RabbitMQ</strong>: BaggageMessagePostProcessor → AMQP 메시지 헤더에 userId/tenantId 삽입</li>
          <li><strong>@Async</strong>: BaggageTaskDecorator → 스레드 전환 시 Baggage 복원</li>
          <li><strong>W3C Baggage</strong>: management.tracing.baggage.remote-fields 설정으로 HTTP 헤더 전파</li>
          <li><strong>MDC</strong>: logging.pattern.level에 %X&#123;userId&#125;/%X&#123;tenantId&#125; 포함</li>
        </ul>
      </div>
    </div>
  );
}
