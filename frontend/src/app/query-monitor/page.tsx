"use client";

import { useState, useEffect, useCallback } from "react";
import { Database, AlertTriangle, Clock, RefreshCw, Zap } from "lucide-react";

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

export default function QueryMonitorPage() {
    const [summary, setSummary] = useState<QueryMonitorSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [lastRefresh, setLastRefresh] = useState<string>("");
    const [error, setError] = useState<string | null>(null);

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

    useEffect(() => {
        fetchSummary();
        const interval = setInterval(fetchSummary, 5000);
        return () => clearInterval(interval);
    }, [fetchSummary]);

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

                {/* Architecture Note */}
                <section className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-3">
                    <h3 className="text-sm font-bold text-neutral-300 uppercase tracking-widest">How It Works</h3>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs text-neutral-500">
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
                    </div>
                </section>
            </div>
        </div>
    );
}
