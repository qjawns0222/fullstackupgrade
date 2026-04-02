"use client";

import { useState, useEffect, useCallback } from "react";
import { Database, AlertTriangle, Clock, RefreshCw, Zap, Search, TableProperties } from "lucide-react";

interface SlowQueryEvent {
    sql: string;
    elapsedTimeMs: number;
    thresholdMs: number;
    callerClass: string;
    callerMethod: string;
    detectedAt: string;
}

interface N1QueryEvent {
    normalizedSql: string;
    executionCount: number;
    thresholdCount: number;
    callerClass: string;
    callerMethod: string;
    detectedAt: string;
}

interface QueryMonitorSummary {
    totalSlowQueries: number;
    totalN1Detections: number;
    recentSlowQueries: SlowQueryEvent[];
    recentN1Alerts: N1QueryEvent[];
}

interface ExplainRow {
    id: number | null;
    selectType: string | null;
    table: string | null;
    type: string | null;
    possibleKeys: string | null;
    key: string | null;
    rows: number | null;
    extra: string | null;
}

interface SlowQueryExplainResult {
    id: string;
    originalSql: string;
    explainRows: ExplainRow[];
    indexRecommendations: string[];
    hasFullTableScan: boolean;
    capturedAt: string;
    executionTimeMs: number;
}

const API_BASE = "http://localhost:8080";

function Badge({ label, variant }: { label: string; variant: "danger" | "warning" | "info" }) {
    const colors = {
        danger: "bg-rose-500/15 text-rose-400 border border-rose-500/30",
        warning: "bg-amber-500/15 text-amber-400 border border-amber-500/30",
        info: "bg-blue-500/15 text-blue-400 border border-blue-500/30",
    };
    return (
        <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${colors[variant]}`}>
            {label}
        </span>
    );
}

function StatCard({
    icon,
    label,
    value,
    accent,
}: {
    icon: React.ReactNode;
    label: string;
    value: number | string;
    accent: string;
}) {
    return (
        <div className={`bg-neutral-900 border rounded-2xl p-6 space-y-3 shadow-xl hover:border-neutral-600 transition-colors ${accent}`}>
            <div className="flex items-center gap-3 text-neutral-400">
                {icon}
                <span className="text-xs font-bold uppercase tracking-widest">{label}</span>
            </div>
            <p className="text-4xl font-black text-neutral-100 font-mono">{value}</p>
        </div>
    );
}

function SlowQueryRow({ event }: { event: SlowQueryEvent }) {
    const overrun = Math.round((event.elapsedTimeMs / event.thresholdMs - 1) * 100);
    return (
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-2 hover:border-rose-500/30 transition-colors">
            <div className="flex items-start justify-between gap-4">
                <code className="text-xs text-rose-300 font-mono bg-neutral-800 px-2 py-1 rounded break-all line-clamp-2">
                    {event.sql}
                </code>
                <Badge label={`+${overrun}% over`} variant="danger" />
            </div>
            <div className="flex items-center gap-4 text-xs text-neutral-500">
                <span className="flex items-center gap-1">
                    <Clock className="w-3 h-3" />
                    {event.elapsedTimeMs}ms
                </span>
                <span className="text-neutral-700">/</span>
                <span>threshold: {event.thresholdMs}ms</span>
                <span className="text-neutral-700">/</span>
                <span className="text-neutral-400 font-mono">
                    {event.callerClass}.{event.callerMethod}
                </span>
            </div>
            <p className="text-[11px] text-neutral-600">{event.detectedAt}</p>
        </div>
    );
}

function N1QueryRow({ event }: { event: N1QueryEvent }) {
    return (
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-2 hover:border-amber-500/30 transition-colors">
            <div className="flex items-start justify-between gap-4">
                <code className="text-xs text-amber-300 font-mono bg-neutral-800 px-2 py-1 rounded break-all line-clamp-2">
                    {event.normalizedSql}
                </code>
                <Badge label={`×${event.executionCount} runs`} variant="warning" />
            </div>
            <div className="flex items-center gap-4 text-xs text-neutral-500">
                <span className="flex items-center gap-1">
                    <Zap className="w-3 h-3" />
                    {event.executionCount} executions
                </span>
                <span className="text-neutral-700">/</span>
                <span>threshold: {event.thresholdCount}</span>
                <span className="text-neutral-700">/</span>
                <span className="text-neutral-400 font-mono">
                    {event.callerClass}.{event.callerMethod}
                </span>
            </div>
            <p className="text-[11px] text-neutral-600">{event.detectedAt}</p>
        </div>
    );
}

function ExplainHistoryRow({ result }: { result: SlowQueryExplainResult }) {
    const [expanded, setExpanded] = useState(false);

    return (
        <div
            className={`bg-neutral-900 border rounded-xl p-4 space-y-3 transition-colors cursor-pointer
                ${result.hasFullTableScan
                    ? "border-rose-500/40 hover:border-rose-500/70"
                    : "border-neutral-800 hover:border-neutral-600"
                }`}
            onClick={() => setExpanded((v) => !v)}
        >
            {/* Header row */}
            <div className="flex items-start justify-between gap-4">
                <code className="text-xs text-blue-300 font-mono bg-neutral-800 px-2 py-1 rounded break-all line-clamp-2 flex-1">
                    {result.originalSql}
                </code>
                <div className="flex items-center gap-2 shrink-0">
                    {result.hasFullTableScan && (
                        <Badge label="FULL SCAN" variant="danger" />
                    )}
                    <Badge label={`${result.executionTimeMs}ms`} variant="info" />
                </div>
            </div>

            {/* Meta */}
            <div className="flex items-center gap-4 text-xs text-neutral-500">
                <span className="flex items-center gap-1">
                    <TableProperties className="w-3 h-3" />
                    {result.explainRows.length} EXPLAIN row{result.explainRows.length !== 1 ? "s" : ""}
                </span>
                <span className="text-neutral-700">/</span>
                <span>{result.indexRecommendations.length} recommendation{result.indexRecommendations.length !== 1 ? "s" : ""}</span>
                <span className="text-neutral-700">/</span>
                <span>{result.capturedAt}</span>
            </div>

            {/* Expanded details */}
            {expanded && (
                <div className="space-y-3 pt-2 border-t border-neutral-800">
                    {/* Index Recommendations */}
                    {result.indexRecommendations.length > 0 && (
                        <div className="space-y-1">
                            <p className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
                                Index Recommendations
                            </p>
                            <ul className="space-y-1">
                                {result.indexRecommendations.map((rec, i) => (
                                    <li key={i} className="flex items-center gap-2 text-xs text-emerald-400">
                                        <Search className="w-3 h-3 shrink-0" />
                                        {rec}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}

                    {/* EXPLAIN rows table */}
                    {result.explainRows.length > 0 && (
                        <div className="space-y-1">
                            <p className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
                                EXPLAIN Output
                            </p>
                            <div className="overflow-x-auto">
                                <table className="w-full text-[11px] text-neutral-400 font-mono">
                                    <thead>
                                        <tr className="text-neutral-600 border-b border-neutral-800">
                                            <th className="text-left pr-3 pb-1">id</th>
                                            <th className="text-left pr-3 pb-1">select_type</th>
                                            <th className="text-left pr-3 pb-1">table</th>
                                            <th className="text-left pr-3 pb-1">type</th>
                                            <th className="text-left pr-3 pb-1">key</th>
                                            <th className="text-left pr-3 pb-1">rows</th>
                                            <th className="text-left pb-1">Extra</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {result.explainRows.map((row, i) => (
                                            <tr key={i} className={row.type === "ALL" ? "text-rose-400" : ""}>
                                                <td className="pr-3 py-0.5">{row.id ?? "-"}</td>
                                                <td className="pr-3 py-0.5">{row.selectType ?? "-"}</td>
                                                <td className="pr-3 py-0.5">{row.table ?? "-"}</td>
                                                <td className={`pr-3 py-0.5 font-bold ${row.type === "ALL" ? "text-rose-400" : "text-neutral-300"}`}>
                                                    {row.type ?? "-"}
                                                </td>
                                                <td className="pr-3 py-0.5">{row.key ?? "-"}</td>
                                                <td className="pr-3 py-0.5">{row.rows ?? "-"}</td>
                                                <td className="py-0.5">{row.extra ?? "-"}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

export default function QueryMonitorPage() {
    const [summary, setSummary] = useState<QueryMonitorSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [lastRefresh, setLastRefresh] = useState<string>("");
    const [error, setError] = useState<string | null>(null);

    const [explainHistory, setExplainHistory] = useState<SlowQueryExplainResult[]>([]);
    const [explainLoading, setExplainLoading] = useState(true);

    const fetchSummary = useCallback(async () => {
        try {
            const res = await fetch(`${API_BASE}/api/query-monitor/summary`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data: QueryMonitorSummary = await res.json();
            setSummary(data);
            setLastRefresh(new Date().toLocaleTimeString());
            setError(null);
        } catch (e: any) {
            setError(e.message || "Failed to fetch");
        } finally {
            setLoading(false);
        }
    }, []);

    const fetchExplainHistory = useCallback(async () => {
        try {
            const res = await fetch(`${API_BASE}/api/query-monitor/explain-history`);
            if (!res.ok) return;
            const data: SlowQueryExplainResult[] = await res.json();
            setExplainHistory(data);
        } catch {
            // silently fail — ES may not be running in dev
        } finally {
            setExplainLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchSummary();
        fetchExplainHistory();
        const interval = setInterval(() => {
            fetchSummary();
            fetchExplainHistory();
        }, 5000);
        return () => clearInterval(interval);
    }, [fetchSummary, fetchExplainHistory]);

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-6xl mx-auto space-y-8">
                {/* Header */}
                <header className="flex items-start justify-between">
                    <div className="space-y-2">
                        <div className="flex items-center gap-3">
                            <div className="bg-purple-600/20 p-2 rounded-lg border border-purple-500/30">
                                <Database className="w-6 h-6 text-purple-400" />
                            </div>
                            <h1 className="text-3xl font-bold tracking-tight">Query Performance Monitor</h1>
                        </div>
                        <p className="text-neutral-400 text-sm ml-14">
                            Real-time slow query detection and N+1 pattern alerting via datasource-proxy.
                        </p>
                    </div>
                    <div className="flex flex-col items-end gap-2">
                        <button
                            onClick={fetchSummary}
                            className="flex items-center gap-2 px-3 py-2 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-sm text-neutral-300 transition-colors border border-neutral-700"
                        >
                            <RefreshCw className="w-3.5 h-3.5" />
                            Refresh
                        </button>
                        {lastRefresh && (
                            <span className="text-[11px] text-neutral-600">Last: {lastRefresh}</span>
                        )}
                    </div>
                </header>

                {error && (
                    <div className="bg-rose-900/20 border border-rose-700/40 rounded-xl p-4 text-rose-300 text-sm">
                        Backend unavailable: {error}. Start the Spring Boot app to see live data.
                    </div>
                )}

                {/* KPI Cards */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                    <StatCard
                        icon={<Clock className="w-4 h-4" />}
                        label="Total Slow Queries Detected"
                        value={loading ? "—" : summary?.totalSlowQueries ?? 0}
                        accent="border-rose-500/20"
                    />
                    <StatCard
                        icon={<AlertTriangle className="w-4 h-4" />}
                        label="Total N+1 Detections"
                        value={loading ? "—" : summary?.totalN1Detections ?? 0}
                        accent="border-amber-500/20"
                    />
                </div>

                {/* Slow Queries */}
                <section className="space-y-4">
                    <div className="flex items-center gap-3">
                        <span className="w-1.5 h-6 bg-rose-500 rounded-full" />
                        <h2 className="text-lg font-bold">Recent Slow Queries</h2>
                        <span className="text-xs text-neutral-500">(threshold: 300ms)</span>
                    </div>
                    {loading ? (
                        <div className="text-neutral-600 text-sm">Loading...</div>
                    ) : summary?.recentSlowQueries.length ? (
                        <div className="space-y-3">
                            {summary.recentSlowQueries.slice().reverse().map((e, i) => (
                                <SlowQueryRow key={i} event={e} />
                            ))}
                        </div>
                    ) : (
                        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-8 text-center text-neutral-600 text-sm">
                            No slow queries detected yet. All queries are running within the 300ms threshold.
                        </div>
                    )}
                </section>

                {/* N+1 Alerts */}
                <section className="space-y-4">
                    <div className="flex items-center gap-3">
                        <span className="w-1.5 h-6 bg-amber-500 rounded-full" />
                        <h2 className="text-lg font-bold">Recent N+1 Detections</h2>
                        <span className="text-xs text-neutral-500">(fires at 5+ identical queries per request)</span>
                    </div>
                    {loading ? (
                        <div className="text-neutral-600 text-sm">Loading...</div>
                    ) : summary?.recentN1Alerts.length ? (
                        <div className="space-y-3">
                            {summary.recentN1Alerts.slice().reverse().map((e, i) => (
                                <N1QueryRow key={i} event={e} />
                            ))}
                        </div>
                    ) : (
                        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-8 text-center text-neutral-600 text-sm">
                            No N+1 patterns detected. DataLoader / join fetching is working correctly.
                        </div>
                    )}
                </section>

                {/* EXPLAIN Analysis History */}
                <section className="space-y-4">
                    <div className="flex items-center gap-3">
                        <span className="w-1.5 h-6 bg-blue-500 rounded-full" />
                        <h2 className="text-lg font-bold">EXPLAIN Analysis History</h2>
                        <span className="text-xs text-neutral-500">(auto-analyzed slow SELECT queries)</span>
                    </div>
                    {explainLoading ? (
                        <div className="text-neutral-600 text-sm">Loading...</div>
                    ) : explainHistory.length ? (
                        <div className="space-y-3">
                            {explainHistory.map((result) => (
                                <ExplainHistoryRow key={result.id} result={result} />
                            ))}
                        </div>
                    ) : (
                        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-8 text-center text-neutral-600 text-sm">
                            No EXPLAIN analyses yet. Slow SELECT queries will be automatically analyzed here.
                        </div>
                    )}
                </section>

                {/* Architecture Note */}
                <section className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-3">
                    <h3 className="text-sm font-bold text-neutral-300 uppercase tracking-widest">How It Works</h3>
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs text-neutral-500">
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">datasource-proxy wrapper</p>
                            <p>Every SQL execution passes through SlowQueryListener before reaching the real DataSource.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">Thread-local N+1 tracking</p>
                            <p>QueryExecutionContext normalizes SQL and counts per-request executions, cleared by a servlet filter.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">Spring Event pipeline</p>
                            <p>SlowQueryEvent / N1QueryEvent are published via ApplicationEventPublisher and stored in QueryAlertStore.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">Auto EXPLAIN + Index Advice</p>
                            <p>Slow SELECT queries trigger EXPLAIN via JdbcTemplate. JSqlParser extracts WHERE columns to recommend indexes. Results persist to Elasticsearch.</p>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    );
}
