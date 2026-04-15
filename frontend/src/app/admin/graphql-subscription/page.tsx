'use client';

import { useState, useEffect, useRef } from 'react';

interface StatusEvent {
    applicationId: string;
    companyName: string;
    position: string;
    newStatus: string;
    userId: string;
    timestamp: number;
}

const STATUS_COLORS: Record<string, string> = {
    APPLIED: 'bg-blue-100 text-blue-800',
    INTERVIEW: 'bg-yellow-100 text-yellow-800',
    REJECTED: 'bg-red-100 text-red-800',
    PASSED: 'bg-green-100 text-green-800',
    OFFER_RECEIVED: 'bg-purple-100 text-purple-800',
};

const SUBSCRIPTION_QUERY = `
subscription {
  applicationStatusChanged {
    applicationId
    companyName
    position
    newStatus
    userId
    timestamp
  }
}
`.trim();

export default function GraphQlSubscriptionPage() {
    const [events, setEvents] = useState<StatusEvent[]>([]);
    const [connected, setConnected] = useState(false);
    const [error, setError] = useState<string>('');
    const [log, setLog] = useState<string[]>([]);
    const wsRef = useRef<WebSocket | null>(null);
    const addLog = (msg: string) =>
        setLog((prev) => [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev.slice(0, 49)]);

    const connect = () => {
        if (wsRef.current) {
            wsRef.current.close();
        }

        addLog('WebSocket 연결 시도 중...');
        const token = localStorage.getItem('accessToken') ?? '';
        const ws = new WebSocket('ws://localhost:8080/graphql-ws', 'graphql-transport-ws');
        wsRef.current = ws;

        ws.onopen = () => {
            addLog('WebSocket 연결됨. 초기화 메시지 전송...');
            ws.send(JSON.stringify({ type: 'connection_init', payload: { Authorization: `Bearer ${token}` } }));
        };

        ws.onmessage = (e) => {
            const msg = JSON.parse(e.data);
            if (msg.type === 'connection_ack') {
                addLog('connection_ack 수신. Subscription 시작...');
                setConnected(true);
                setError('');
                ws.send(JSON.stringify({ id: '1', type: 'subscribe', payload: { query: SUBSCRIPTION_QUERY } }));
            } else if (msg.type === 'next') {
                const event: StatusEvent = msg.payload?.data?.applicationStatusChanged;
                if (event) {
                    addLog(`이벤트 수신: ${event.companyName} → ${event.newStatus}`);
                    setEvents((prev) => [event, ...prev.slice(0, 99)]);
                }
            } else if (msg.type === 'error') {
                const errMsg = msg.payload?.[0]?.message ?? '알 수 없는 오류';
                addLog(`오류: ${errMsg}`);
                setError(errMsg);
            } else if (msg.type === 'complete') {
                addLog('Subscription 완료됨.');
                setConnected(false);
            }
        };

        ws.onerror = () => {
            addLog('WebSocket 오류 발생');
            setError('WebSocket 연결 오류');
            setConnected(false);
        };

        ws.onclose = (e) => {
            addLog(`WebSocket 닫힘 (code: ${e.code})`);
            setConnected(false);
        };
    };

    const disconnect = () => {
        wsRef.current?.close();
        wsRef.current = null;
        setConnected(false);
        addLog('연결 종료됨.');
    };

    useEffect(() => () => { wsRef.current?.close(); }, []);

    return (
        <div className="p-6 max-w-6xl mx-auto">
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-gray-900">GraphQL Subscription 실시간 알림</h1>
                <p className="text-gray-500 mt-1">
                    graphql-transport-ws 프로토콜로 지원서 상태 변경을 실시간 스트리밍합니다.
                </p>
            </div>

            {/* 연결 상태 + 버튼 */}
            <div className="flex items-center gap-4 mb-6">
                <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium ${connected ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                    <span className={`w-2 h-2 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-gray-400'}`} />
                    {connected ? '연결됨' : '연결 안 됨'}
                </div>
                <button
                    onClick={connected ? disconnect : connect}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${connected ? 'bg-red-600 hover:bg-red-700 text-white' : 'bg-blue-600 hover:bg-blue-700 text-white'}`}
                >
                    {connected ? '연결 해제' : 'WebSocket 연결'}
                </button>
                {events.length > 0 && (
                    <button onClick={() => setEvents([])} className="px-3 py-2 text-sm text-gray-500 hover:text-gray-700 border rounded-lg">
                        이벤트 초기화
                    </button>
                )}
            </div>

            {error && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{error}</div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* 이벤트 스트림 */}
                <div>
                    <div className="flex items-center justify-between mb-2">
                        <p className="text-sm font-medium text-gray-700">수신된 이벤트</p>
                        <span className="text-xs text-gray-400">{events.length}건</span>
                    </div>
                    <div className="border rounded-lg bg-white divide-y divide-gray-100 min-h-[300px] max-h-[500px] overflow-auto">
                        {events.length === 0 ? (
                            <div className="flex items-center justify-center h-48 text-gray-400 text-sm">
                                {connected ? '지원서 상태를 변경하면 이벤트가 여기에 표시됩니다.' : 'WebSocket을 연결하세요.'}
                            </div>
                        ) : (
                            events.map((ev, i) => (
                                <div key={i} className="p-3 hover:bg-gray-50">
                                    <div className="flex items-start justify-between gap-2">
                                        <div>
                                            <p className="text-sm font-medium text-gray-900">{ev.companyName}</p>
                                            <p className="text-xs text-gray-500">{ev.position}</p>
                                        </div>
                                        <span className={`shrink-0 px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[ev.newStatus] ?? 'bg-gray-100 text-gray-700'}`}>
                                            {ev.newStatus}
                                        </span>
                                    </div>
                                    <p className="text-xs text-gray-400 mt-1">
                                        {new Date(ev.timestamp).toLocaleTimeString()} · 지원서 #{ev.applicationId}
                                    </p>
                                </div>
                            ))
                        )}
                    </div>
                </div>

                {/* 연결 로그 */}
                <div>
                    <p className="text-sm font-medium text-gray-700 mb-2">연결 로그</p>
                    <div className="border rounded-lg bg-gray-900 p-3 min-h-[300px] max-h-[500px] overflow-auto font-mono text-xs text-green-400 space-y-1">
                        {log.length === 0 ? (
                            <p className="text-gray-500">로그 없음</p>
                        ) : (
                            log.map((line, i) => <p key={i}>{line}</p>)
                        )}
                    </div>
                </div>
            </div>

            {/* 설명 */}
            <div className="mt-6 p-4 bg-blue-50 rounded-lg border border-blue-200">
                <p className="text-xs font-semibold text-blue-800 mb-2">GraphQL Subscription 구현 포인트</p>
                <ul className="text-xs text-blue-700 space-y-1">
                    <li>프로토콜: graphql-transport-ws (graphql-ws) — HTTP 폴링 없는 진짜 스트리밍</li>
                    <li>서버: @SubscriptionMapping + Flux&lt;T&gt; 반환 — Spring for GraphQL 1.2.4</li>
                    <li>브로드캐스트: Sinks.many().multicast().onBackpressureBuffer() — 유저별 필터링</li>
                    <li>트리거: changeStatus() 호출 시 ApplicationSubscriptionService.publish() 실행</li>
                    <li>격리: statusChangesForUser(userId)로 본인 이벤트만 수신</li>
                </ul>
            </div>

            {/* Subscription 쿼리 */}
            <div className="mt-4">
                <p className="text-sm font-medium text-gray-700 mb-2">Subscription 쿼리</p>
                <pre className="bg-gray-900 text-green-400 text-xs p-4 rounded-lg overflow-auto">{SUBSCRIPTION_QUERY}</pre>
            </div>
        </div>
    );
}
