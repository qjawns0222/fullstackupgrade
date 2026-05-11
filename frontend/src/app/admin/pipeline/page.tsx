'use client';

import { useQuery } from '@tanstack/react-query';

interface PipelineStats {
  processed: number;
  dropped: number;
}

export default function PipelineDashboardPage() {
  const { data: stats, isLoading, dataUpdatedAt } = useQuery<PipelineStats>({
    queryKey: ['pipeline-stats'],
    queryFn: () => fetch('/api/audit/pipeline/stats').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const total = (stats?.processed ?? 0) + (stats?.dropped ?? 0);
  const dropRate = total > 0 ? ((stats?.dropped ?? 0) / total * 100).toFixed(2) : '0.00';
  const lastUpdated = dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString() : '-';

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Reactive Audit Pipeline</h1>
        <p className="text-sm text-gray-500 mt-1">
          RabbitMQ → Flux 백프레셔 파이프라인 처리 현황 (5초 자동 갱신)
        </p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="bg-white border rounded-lg p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide">처리 완료</p>
          {isLoading ? (
            <p className="text-3xl font-bold text-gray-300 mt-1">...</p>
          ) : (
            <p className="text-3xl font-bold text-green-600 mt-1">
              {stats?.processed.toLocaleString() ?? 0}
            </p>
          )}
          <p className="text-xs text-gray-400 mt-1">ES 저장 성공 건수</p>
        </div>

        <div className="bg-white border rounded-lg p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide">드롭</p>
          {isLoading ? (
            <p className="text-3xl font-bold text-gray-300 mt-1">...</p>
          ) : (
            <p className="text-3xl font-bold text-red-500 mt-1">
              {stats?.dropped.toLocaleString() ?? 0}
            </p>
          )}
          <p className="text-xs text-gray-400 mt-1">백프레셔 버퍼 초과 드롭</p>
        </div>

        <div className="bg-white border rounded-lg p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide">드롭률</p>
          {isLoading ? (
            <p className="text-3xl font-bold text-gray-300 mt-1">...</p>
          ) : (
            <p className={`text-3xl font-bold mt-1 ${parseFloat(dropRate) > 1 ? 'text-red-500' : 'text-gray-800'}`}>
              {dropRate}%
            </p>
          )}
          <p className="text-xs text-gray-400 mt-1">dropped / (processed + dropped)</p>
        </div>
      </div>

      {/* Pipeline Architecture */}
      <div className="bg-white border rounded-lg p-5 shadow-sm mb-6">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">파이프라인 구조</h2>
        <div className="flex items-center gap-2 flex-wrap text-sm">
          {[
            { label: 'RabbitMQ', color: 'bg-orange-100 text-orange-800' },
            { label: '→', color: '' },
            { label: '@RabbitListener', color: 'bg-blue-100 text-blue-800' },
            { label: '→', color: '' },
            { label: 'Sinks.many() (emit)', color: 'bg-purple-100 text-purple-800' },
            { label: '→', color: '' },
            { label: 'onBackpressureBuffer(1000)', color: 'bg-yellow-100 text-yellow-800' },
            { label: '→', color: '' },
            { label: 'bufferTimeout(50, 100ms)', color: 'bg-indigo-100 text-indigo-800' },
            { label: '→', color: '' },
            { label: 'Schedulers.boundedElastic()', color: 'bg-teal-100 text-teal-800' },
            { label: '→', color: '' },
            { label: 'Elasticsearch (batch)', color: 'bg-green-100 text-green-800' },
          ].map((step, i) =>
            step.color ? (
              <span key={i} className={`px-2 py-1 rounded text-xs font-medium ${step.color}`}>
                {step.label}
              </span>
            ) : (
              <span key={i} className="text-gray-400 font-bold">{step.label}</span>
            )
          )}
        </div>
      </div>

      {/* Config Info */}
      <div className="bg-gray-50 border rounded-lg p-5">
        <h2 className="text-sm font-semibold text-gray-700 mb-3">설정값</h2>
        <div className="grid grid-cols-2 gap-3 text-sm">
          {[
            { key: '백프레셔 버퍼 크기', value: '1,000 건' },
            { key: '배치 최대 크기', value: '50 건' },
            { key: '배치 타임아웃', value: '100 ms' },
            { key: '스케줄러', value: 'boundedElastic' },
          ].map(({ key, value }) => (
            <div key={key} className="flex justify-between bg-white rounded px-3 py-2 border">
              <span className="text-gray-500">{key}</span>
              <span className="font-mono font-medium text-gray-800">{value}</span>
            </div>
          ))}
        </div>
        <p className="text-xs text-gray-400 mt-3 text-right">마지막 갱신: {lastUpdated}</p>
      </div>
    </div>
  );
}
