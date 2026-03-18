'use client';

import { useState } from 'react';
import { gql } from '@/lib/graphql';

interface Application {
    id: string;
    companyName: string;
    position: string;
    status: string;
    appliedDate: string;
}

interface TrendStat {
    id: string;
    techStack: string;
    count: string;
    recordedAt: string;
}

const MY_APPLICATIONS_QUERY = `
  query {
    myApplications {
      id
      companyName
      position
      status
      appliedDate
    }
  }
`;

const TRENDS_QUERY = `
  query {
    trends {
      id
      techStack
      count
      recordedAt
    }
  }
`;

const DASHBOARD_QUERY = `
  query {
    dashboard {
      userId
      data
      timestamp
    }
  }
`;

export default function GraphQlExplorerPage() {
    const [activeQuery, setActiveQuery] = useState<string>('');
    const [customQuery, setCustomQuery] = useState<string>(MY_APPLICATIONS_QUERY.trim());
    const [result, setResult] = useState<unknown>(null);
    const [error, setError] = useState<string>('');
    const [loading, setLoading] = useState(false);
    const [executionTime, setExecutionTime] = useState<number | null>(null);

    const executeQuery = async (query: string) => {
        setLoading(true);
        setError('');
        setResult(null);
        const start = performance.now();
        try {
            const data = await gql<unknown>(query);
            setResult(data);
            setExecutionTime(Math.round(performance.now() - start));
        } catch (e: unknown) {
            setError(e instanceof Error ? e.message : 'Unknown error');
        } finally {
            setLoading(false);
        }
    };

    const presets = [
        { label: 'My Applications (N+1 방지 DataLoader)', query: MY_APPLICATIONS_QUERY },
        { label: 'Trends (Over-fetching 없음)', query: TRENDS_QUERY },
        { label: 'Dashboard (캐시 활용)', query: DASHBOARD_QUERY },
    ];

    return (
        <div className="p-6 max-w-6xl mx-auto">
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-gray-900">GraphQL Explorer</h1>
                <p className="text-gray-500 mt-1">
                    Spring for GraphQL 엔드포인트 테스트 - N+1 문제 해결, Over-fetching 방지
                </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div className="space-y-4">
                    <div>
                        <p className="text-sm font-medium text-gray-700 mb-2">프리셋 쿼리</p>
                        <div className="space-y-2">
                            {presets.map((preset) => (
                                <button
                                    key={preset.label}
                                    onClick={() => {
                                        setCustomQuery(preset.query.trim());
                                        setActiveQuery(preset.label);
                                    }}
                                    className={`w-full text-left px-4 py-2 rounded-lg border text-sm transition-colors ${
                                        activeQuery === preset.label
                                            ? 'border-blue-500 bg-blue-50 text-blue-700'
                                            : 'border-gray-200 hover:border-gray-300 text-gray-700'
                                    }`}
                                >
                                    {preset.label}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div>
                        <label className="text-sm font-medium text-gray-700 block mb-1">
                            GraphQL Query
                        </label>
                        <textarea
                            value={customQuery}
                            onChange={(e) => setCustomQuery(e.target.value)}
                            rows={12}
                            className="w-full font-mono text-sm border border-gray-300 rounded-lg p-3 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-gray-900 text-green-400"
                            placeholder="GraphQL query를 입력하세요..."
                        />
                    </div>

                    <button
                        onClick={() => executeQuery(customQuery)}
                        disabled={loading || !customQuery.trim()}
                        className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white font-medium py-2.5 px-4 rounded-lg transition-colors"
                    >
                        {loading ? '실행 중...' : '쿼리 실행'}
                    </button>

                    {executionTime !== null && !loading && (
                        <p className="text-xs text-gray-500 text-center">
                            응답 시간: {executionTime}ms
                        </p>
                    )}
                </div>

                <div>
                    <label className="text-sm font-medium text-gray-700 block mb-1">
                        응답 결과
                    </label>
                    <div className="border border-gray-300 rounded-lg bg-gray-900 p-4 min-h-[400px] overflow-auto">
                        {loading && (
                            <div className="flex items-center justify-center h-32">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-400" />
                            </div>
                        )}
                        {error && (
                            <pre className="text-red-400 text-sm whitespace-pre-wrap">{error}</pre>
                        )}
                        {result && !loading && (
                            <pre className="text-green-400 text-sm whitespace-pre-wrap">
                                {JSON.stringify(result, null, 2)}
                            </pre>
                        )}
                        {!result && !error && !loading && (
                            <p className="text-gray-500 text-sm text-center mt-8">
                                쿼리를 선택하거나 직접 입력 후 실행하세요
                            </p>
                        )}
                    </div>

                    <div className="mt-4 p-4 bg-blue-50 rounded-lg border border-blue-200">
                        <p className="text-xs font-semibold text-blue-800 mb-2">GraphQL 적용 효과</p>
                        <ul className="text-xs text-blue-700 space-y-1">
                            <li>N+1 문제: DataLoader로 User 쿼리 N→1 배치 처리</li>
                            <li>Over-fetching: 필요한 필드만 선택적 요청</li>
                            <li>단일 엔드포인트: /graphql 하나로 모든 데이터</li>
                            <li>타입 안전성: 스키마 기반 쿼리 검증</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    );
}
