'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

interface DomainEventDto {
  id: number;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  eventPayload: string;
  actor: string | null;
  occurredAt: string;
}

interface AggregateTypeStat {
  aggregateType: string;
  count: number;
}

interface DomainEventStats {
  totalEvents: number;
  aggregateTypes: AggregateTypeStat[];
}

export default function EventSourcingPage() {
  const [aggregateType, setAggregateType] = useState('');
  const [aggregateId, setAggregateId] = useState('');
  const [replayResult, setReplayResult] = useState<DomainEventDto[] | null>(null);
  const [replayLoading, setReplayLoading] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const { data: stats } = useQuery<DomainEventStats>({
    queryKey: ['es-stats'],
    queryFn: () => fetch('/api/event-sourcing/stats').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const { data: recent, isLoading } = useQuery<DomainEventDto[]>({
    queryKey: ['es-recent'],
    queryFn: () => fetch('/api/event-sourcing/recent?limit=30').then((r) => r.json()),
    refetchInterval: 5000,
  });

  async function handleReplay() {
    if (!aggregateType || !aggregateId) return;
    setReplayLoading(true);
    try {
      const data = await fetch(`/api/event-sourcing/aggregate/${aggregateType}/${aggregateId}`).then((r) => r.json());
      setReplayResult(data);
    } finally {
      setReplayLoading(false);
    }
  }

  const displayEvents = replayResult ?? recent ?? [];

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">이벤트 소싱 감사 추적</h1>
        <p className="text-sm text-gray-500 mt-1">도메인 이벤트 append-only 저장 및 집계 리플레이</p>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-white border rounded-lg p-4 shadow-sm col-span-2 md:col-span-1">
            <p className="text-xs text-gray-500">전체 이벤트</p>
            <p className="text-3xl font-bold mt-1 text-indigo-600">{stats.totalEvents}</p>
          </div>
          {stats.aggregateTypes.slice(0, 3).map((s) => (
            <div key={s.aggregateType} className="bg-white border rounded-lg p-4 shadow-sm">
              <p className="text-xs text-gray-500">{s.aggregateType}</p>
              <p className="text-3xl font-bold mt-1 text-gray-800">{s.count}</p>
            </div>
          ))}
        </div>
      )}

      {/* Replay 검색 */}
      <div className="bg-white border rounded-lg p-4 shadow-sm mb-6">
        <h2 className="text-sm font-semibold text-gray-700 mb-3">집계 리플레이</h2>
        <div className="flex gap-3 items-end">
          <div>
            <label className="text-xs text-gray-500 block mb-1">Aggregate Type</label>
            <input
              value={aggregateType}
              onChange={(e) => setAggregateType(e.target.value)}
              placeholder="e.g. Resume"
              className="border rounded px-3 py-2 text-sm w-40"
            />
          </div>
          <div>
            <label className="text-xs text-gray-500 block mb-1">Aggregate ID</label>
            <input
              value={aggregateId}
              onChange={(e) => setAggregateId(e.target.value)}
              placeholder="e.g. 42"
              className="border rounded px-3 py-2 text-sm w-40"
            />
          </div>
          <button
            onClick={handleReplay}
            disabled={replayLoading || !aggregateType || !aggregateId}
            className="px-4 py-2 bg-indigo-600 text-white text-sm rounded hover:bg-indigo-700 disabled:opacity-50"
          >
            {replayLoading ? '조회 중...' : '리플레이'}
          </button>
          {replayResult && (
            <button
              onClick={() => setReplayResult(null)}
              className="px-4 py-2 bg-gray-100 text-gray-700 text-sm rounded hover:bg-gray-200"
            >
              초기화
            </button>
          )}
        </div>
        {replayResult && (
          <p className="text-xs text-indigo-600 mt-2">
            {aggregateType} #{aggregateId} — {replayResult.length}개 이벤트
          </p>
        )}
      </div>

      {/* Event Table */}
      <div className="bg-white border rounded-lg shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between">
          <span className="text-sm font-semibold text-gray-700">
            {replayResult ? '리플레이 결과' : '최근 이벤트 (30건)'}
          </span>
          {isLoading && !replayResult && (
            <span className="text-xs text-gray-400">로딩 중...</span>
          )}
        </div>
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">ID</th>
              <th className="px-4 py-3 text-left">Aggregate</th>
              <th className="px-4 py-3 text-left">Event Type</th>
              <th className="px-4 py-3 text-left">Actor</th>
              <th className="px-4 py-3 text-left">발생 시각</th>
              <th className="px-4 py-3 text-left">Payload</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {displayEvents.length === 0 ? (
              <tr>
                <td colSpan={6} className="text-center py-10 text-gray-400">이벤트 없음</td>
              </tr>
            ) : (
              displayEvents.map((ev) => (
                <>
                  <tr
                    key={ev.id}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => setExpandedId(expandedId === ev.id ? null : ev.id)}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">{ev.id}</td>
                    <td className="px-4 py-3">
                      <span className="bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded text-xs font-medium">
                        {ev.aggregateType}
                      </span>
                      <span className="ml-1 text-gray-500 text-xs">#{ev.aggregateId}</span>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-gray-800">{ev.eventType}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">{ev.actor ?? '-'}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {ev.occurredAt.replace('T', ' ').slice(0, 19)}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-400 truncate max-w-xs">
                      {ev.eventPayload.slice(0, 60)}{ev.eventPayload.length > 60 ? '...' : ''}
                    </td>
                  </tr>
                  {expandedId === ev.id && (
                    <tr key={`${ev.id}-expanded`} className="bg-gray-50">
                      <td colSpan={6} className="px-6 py-3">
                        <pre className="text-xs text-gray-700 whitespace-pre-wrap break-all bg-white border rounded p-3">
                          {JSON.stringify(JSON.parse(ev.eventPayload), null, 2)}
                        </pre>
                      </td>
                    </tr>
                  )}
                </>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
