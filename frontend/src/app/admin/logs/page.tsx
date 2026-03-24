'use client';

import { useEffect, useState, useCallback } from 'react';
import axios from '@/lib/axios';

interface AccessLog {
    id: string;
    requestId: string;
    method: string;
    path: string;
    status: number;
    durationMs: number;
    clientIp: string;
    userId: string | null;
    timestamp: string;
}

interface PageResponse<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
}

interface LogSummary {
    totalRequests: number;
}

const METHOD_COLORS: Record<string, string> = {
    GET: 'text-blue-400',
    POST: 'text-green-400',
    PUT: 'text-yellow-400',
    PATCH: 'text-orange-400',
    DELETE: 'text-red-400',
};

function statusBadge(status: number): string {
    if (status < 300) return 'bg-green-700 text-green-100';
    if (status < 400) return 'bg-blue-700 text-blue-100';
    if (status < 500) return 'bg-yellow-700 text-yellow-100';
    return 'bg-red-700 text-red-100';
}

export default function HttpAccessLogPage() {
    const [logs, setLogs] = useState<AccessLog[]>([]);
    const [summary, setSummary] = useState<LogSummary | null>(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [filterStatus, setFilterStatus] = useState('');
    const [filterUserId, setFilterUserId] = useState('');
    const [autoRefresh, setAutoRefresh] = useState(false);

    const fetchLogs = useCallback(async () => {
        setLoading(true);
        try {
            const params: Record<string, string | number> = { page, size: 20 };
            if (filterStatus) params.status = parseInt(filterStatus, 10);
            if (filterUserId) params.userId = filterUserId;

            const res = await axios.get<PageResponse<AccessLog>>('http://localhost:8080/api/logs', { params });
            setLogs(res.data.content);
            setTotalPages(res.data.totalPages);
            setTotalElements(res.data.totalElements);
        } catch (err) {
            console.error('Failed to fetch access logs', err);
        } finally {
            setLoading(false);
        }
    }, [page, filterStatus, filterUserId]);

    const fetchSummary = useCallback(async () => {
        try {
            const res = await axios.get<LogSummary>('http://localhost:8080/api/logs/summary');
            setSummary(res.data);
        } catch (err) {
            console.error('Failed to fetch log summary', err);
        }
    }, []);

    useEffect(() => {
        fetchLogs();
        fetchSummary();
    }, [fetchLogs, fetchSummary]);

    useEffect(() => {
        if (!autoRefresh) return;
        const id = setInterval(() => {
            fetchLogs();
            fetchSummary();
        }, 5000);
        return () => clearInterval(id);
    }, [autoRefresh, fetchLogs, fetchSummary]);

    const handleFilterApply = () => {
        setPage(0);
        fetchLogs();
    };

    const handleFilterClear = () => {
        setFilterStatus('');
        setFilterUserId('');
        setPage(0);
    };

    return (
        <div className="min-h-screen bg-slate-900 text-white p-8">
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-3xl font-bold text-purple-400">HTTP Access Logs</h1>
                    <p className="text-slate-400 mt-1 text-sm">
                        Structured request log — powered by logstash-logback-encoder + Elasticsearch
                    </p>
                </div>
                <button
                    onClick={() => setAutoRefresh(v => !v)}
                    className={`px-4 py-2 rounded text-sm font-semibold transition-colors ${
                        autoRefresh
                            ? 'bg-purple-600 hover:bg-purple-700'
                            : 'bg-slate-700 hover:bg-slate-600'
                    }`}
                >
                    {autoRefresh ? 'Auto-refresh ON' : 'Auto-refresh OFF'}
                </button>
            </div>

            {/* Summary card */}
            {summary && (
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
                    <div className="bg-slate-800 rounded-lg p-5 border border-slate-700">
                        <p className="text-slate-400 text-xs uppercase tracking-wider mb-1">Total Requests</p>
                        <p className="text-3xl font-bold text-purple-300">{summary.totalRequests.toLocaleString()}</p>
                    </div>
                    <div className="bg-slate-800 rounded-lg p-5 border border-slate-700">
                        <p className="text-slate-400 text-xs uppercase tracking-wider mb-1">Current Page</p>
                        <p className="text-3xl font-bold text-blue-300">{totalElements.toLocaleString()}</p>
                        <p className="text-slate-500 text-xs mt-1">matching entries</p>
                    </div>
                    <div className="bg-slate-800 rounded-lg p-5 border border-slate-700">
                        <p className="text-slate-400 text-xs uppercase tracking-wider mb-1">Log Format</p>
                        <p className="text-sm font-mono text-green-400">JSON (Logstash)</p>
                        <p className="text-slate-500 text-xs mt-1">MDC fields as top-level keys</p>
                    </div>
                </div>
            )}

            {/* Filters */}
            <div className="bg-slate-800 rounded-lg p-4 border border-slate-700 mb-6 flex flex-wrap gap-3 items-end">
                <div>
                    <label className="block text-xs text-slate-400 mb-1">HTTP Status</label>
                    <input
                        type="number"
                        placeholder="e.g. 500"
                        value={filterStatus}
                        onChange={e => setFilterStatus(e.target.value)}
                        className="bg-slate-700 border border-slate-600 rounded px-3 py-1.5 text-sm w-28 focus:outline-none focus:ring-2 focus:ring-purple-500"
                    />
                </div>
                <div>
                    <label className="block text-xs text-slate-400 mb-1">User ID</label>
                    <input
                        type="text"
                        placeholder="e.g. user-42"
                        value={filterUserId}
                        onChange={e => setFilterUserId(e.target.value)}
                        className="bg-slate-700 border border-slate-600 rounded px-3 py-1.5 text-sm w-36 focus:outline-none focus:ring-2 focus:ring-purple-500"
                    />
                </div>
                <button
                    onClick={handleFilterApply}
                    className="bg-purple-600 hover:bg-purple-700 px-4 py-1.5 rounded text-sm font-medium transition-colors"
                >
                    Apply
                </button>
                <button
                    onClick={handleFilterClear}
                    className="bg-slate-600 hover:bg-slate-500 px-4 py-1.5 rounded text-sm font-medium transition-colors"
                >
                    Clear
                </button>
                <button
                    onClick={() => { fetchLogs(); fetchSummary(); }}
                    className="ml-auto bg-slate-700 hover:bg-slate-600 px-4 py-1.5 rounded text-sm font-medium transition-colors"
                >
                    Refresh
                </button>
            </div>

            {/* Table */}
            <div className="bg-slate-800 rounded-lg border border-slate-700 overflow-x-auto shadow-lg">
                {loading ? (
                    <div className="p-12 text-center text-slate-400">Loading...</div>
                ) : logs.length === 0 ? (
                    <div className="p-12 text-center text-slate-500">
                        No access logs yet. Make a request to the backend to see entries here.
                    </div>
                ) : (
                    <table className="w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-slate-700 text-xs text-slate-400 uppercase tracking-wide">
                                <th className="p-3">Timestamp</th>
                                <th className="p-3">Method</th>
                                <th className="p-3">Path</th>
                                <th className="p-3">Status</th>
                                <th className="p-3 text-right">Duration</th>
                                <th className="p-3">Client IP</th>
                                <th className="p-3">User</th>
                                <th className="p-3 font-mono">Request ID</th>
                            </tr>
                        </thead>
                        <tbody>
                            {logs.map(log => (
                                <tr
                                    key={log.id ?? log.requestId}
                                    className="border-b border-slate-700/50 hover:bg-slate-700/30 transition-colors"
                                >
                                    <td className="p-3 text-xs text-slate-400 whitespace-nowrap">
                                        {new Date(log.timestamp).toLocaleString()}
                                    </td>
                                    <td className={`p-3 font-bold font-mono ${METHOD_COLORS[log.method] ?? 'text-slate-300'}`}>
                                        {log.method}
                                    </td>
                                    <td className="p-3 font-mono text-slate-300 max-w-xs truncate" title={log.path}>
                                        {log.path}
                                    </td>
                                    <td className="p-3">
                                        <span className={`px-2 py-0.5 rounded text-xs font-bold ${statusBadge(log.status)}`}>
                                            {log.status}
                                        </span>
                                    </td>
                                    <td className="p-3 text-right font-mono">
                                        <span className={log.durationMs > 500 ? 'text-red-400' : log.durationMs > 200 ? 'text-yellow-400' : 'text-green-400'}>
                                            {log.durationMs}ms
                                        </span>
                                    </td>
                                    <td className="p-3 font-mono text-slate-400 text-xs">{log.clientIp}</td>
                                    <td className="p-3 text-xs text-slate-400">{log.userId ?? '-'}</td>
                                    <td className="p-3 font-mono text-xs text-slate-500 max-w-xs truncate" title={log.requestId}>
                                        {log.requestId}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
                <div className="mt-4 flex gap-2 justify-center">
                    <button
                        disabled={page === 0}
                        onClick={() => setPage(p => p - 1)}
                        className="px-3 py-1 bg-slate-700 hover:bg-slate-600 disabled:opacity-40 rounded text-sm"
                    >
                        Prev
                    </button>
                    <span className="px-3 py-1 text-slate-400 text-sm">
                        Page {page + 1} / {totalPages}
                    </span>
                    <button
                        disabled={page >= totalPages - 1}
                        onClick={() => setPage(p => p + 1)}
                        className="px-3 py-1 bg-slate-700 hover:bg-slate-600 disabled:opacity-40 rounded text-sm"
                    >
                        Next
                    </button>
                </div>
            )}

            {/* MDC Fields info panel */}
            <div className="mt-8 bg-slate-800 rounded-lg border border-slate-700 p-6">
                <h2 className="text-lg font-semibold text-purple-300 mb-3">MDC Fields in JSON Log</h2>
                <p className="text-slate-400 text-sm mb-4">
                    Every log line produced during a request carries these fields as top-level Elasticsearch keys,
                    enabling zero-parsing cross-service correlation in Kibana/Grafana.
                </p>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {['requestId', 'userId', 'traceId', 'spanId', 'httpMethod', 'httpPath', 'httpStatus', 'durationMs', 'clientIp'].map(field => (
                        <div key={field} className="bg-slate-900 rounded px-3 py-2 font-mono text-xs text-purple-300 border border-slate-700">
                            {field}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}
