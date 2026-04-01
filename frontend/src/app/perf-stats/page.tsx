"use client";

import { useState, useEffect, useCallback } from "react";
import { Activity, AlertTriangle, Clock, RefreshCw, TrendingUp, Zap } from "lucide-react";

interface EndpointStats {
    endpoint: string;
    totalCalls: number;
    errorCalls: number;
    errorRate: number;
    avgMs: number;
    p50Ms: number;
    p95Ms: number;
    p99Ms: number;
    minMs: number;
    maxMs: number;
    windowSeconds: number;
}

interface StoreSummary {
    totalRequests: number;
    totalErrors: number;
    trackedEndpoints: number;
    windowSeconds: number;
}

const API_BASE = "http://localhost:8080";

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

function LatencyBar({ value, max, color }: { value: number; max: number; color: string }) {
    const pct = max > 0 ? Math.min((value / max) * 100, 100) : 0;
    return (
        <div className="flex items-center gap-2">
            <div className="flex-1 bg-neutral-800 rounded-full h-1.5">
                <div
                    className={`h-1.5 rounded-full ${color}`}
                    style={{ width: `${pct}%` }}
                />
            </div>
            <span className="text-xs font-mono text-neutral-400 w-14 text-right">{value}ms</span>
        </div>
    );
}

function ErrorRateBadge({ rate }: { rate: number }) {
    const pct = (rate * 100).toFixed(1);
    if (rate === 0) return <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">0%</span>;
    if (rate < 0.05) return <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-500/15 text-amber-400 border border-amber-500/30">{pct}%</span>;
    return <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-rose-500/15 text-rose-400 border border-rose-500/30">{pct}%</span>;
}

function EndpointRow({ stat, maxAvg }: { stat: EndpointStats; maxAvg: number }) {
    const [method, ...pathParts] = stat.endpoint.split(" ");
    const path = pathParts.join(" ");
    const methodColor: Record<string, string> = {
        GET: "text-sky-400",
        POST: "text-emerald-400",
        PUT: "text-amber-400",
        DELETE: "text-rose-400",
        PATCH: "text-violet-400",
    };

    return (
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-3 hover:border-neutral-600 transition-colors">
            <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-2 min-w-0">
                    <span className={`text-xs font-bold font-mono w-14 shrink-0 ${methodColor[method] ?? "text-neutral-400"}`}>
                        {method}
                    </span>
                    <code className="text-sm text-neutral-200 font-mono truncate">{path}</code>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                    <ErrorRateBadge rate={stat.errorRate} />
                    <span className="text-xs text-neutral-500 font-mono">{stat.totalCalls.toLocaleString()} calls</span>
                </div>
            </div>

            <div className="grid grid-cols-3 gap-4 text-xs text-neutral-500">
                <div className="space-y-1">
                    <span className="text-neutral-600 uppercase tracking-widest text-[10px]">avg</span>
                    <p className="text-neutral-300 font-mono font-semibold">{stat.avgMs.toFixed(1)}ms</p>
                </div>
                <div className="space-y-1">
                    <span className="text-neutral-600 uppercase tracking-widest text-[10px]">p95</span>
                    <p className="text-amber-400 font-mono font-semibold">{stat.p95Ms}ms</p>
                </div>
                <div className="space-y-1">
                    <span className="text-neutral-600 uppercase tracking-widest text-[10px]">p99</span>
                    <p className="text-rose-400 font-mono font-semibold">{stat.p99Ms}ms</p>
                </div>
            </div>

            <div className="space-y-1">
                <div className="flex justify-between text-[10px] text-neutral-600 mb-1">
                    <span>p50 ({stat.p50Ms}ms)</span>
                    <span>p95 ({stat.p95Ms}ms)</span>
                    <span>p99 ({stat.p99Ms}ms)</span>
                </div>
                <LatencyBar value={stat.p50Ms} max={stat.p99Ms || 1} color="bg-sky-500" />
                <LatencyBar value={stat.p95Ms} max={stat.p99Ms || 1} color="bg-amber-500" />
                <LatencyBar value={stat.p99Ms} max={stat.p99Ms || 1} color="bg-rose-500" />
            </div>

            <div className="flex items-center gap-4 text-[11px] text-neutral-600">
                <span>min: {stat.minMs}ms</span>
                <span>/</span>
                <span>max: {stat.maxMs}ms</span>
                <span>/</span>
                <span>errors: {stat.errorCalls}</span>
                <span>/</span>
                <span>window: {stat.windowSeconds}s</span>
            </div>
        </div>
    );
}

export default function PerfStatsPage() {
    const [stats, setStats] = useState<EndpointStats[]>([]);
    const [summary, setSummary] = useState<StoreSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [errorsOnly, setErrorsOnly] = useState(false);
    const [lastRefresh, setLastRefresh] = useState("");
    const [error, setError] = useState<string | null>(null);

    const fetchData = useCallback(async () => {
        try {
            const [statsRes, summaryRes] = await Promise.all([
                fetch(`${API_BASE}/api/perf/stats?errorsOnly=${errorsOnly}`),
                fetch(`${API_BASE}/api/perf/summary`),
            ]);
            if (!statsRes.ok || !summaryRes.ok) throw new Error("HTTP error");
            const [statsData, summaryData] = await Promise.all([
                statsRes.json(),
                summaryRes.json(),
            ]);
            setStats(statsData);
            setSummary(summaryData);
            setLastRefresh(new Date().toLocaleTimeString());
            setError(null);
        } catch (e: unknown) {
            setError(e instanceof Error ? e.message : "Failed to fetch");
        } finally {
            setLoading(false);
        }
    }, [errorsOnly]);

    useEffect(() => {
        fetchData();
        const interval = setInterval(fetchData, 5000);
        return () => clearInterval(interval);
    }, [fetchData]);

    const maxAvg = Math.max(...stats.map((s) => s.avgMs), 1);

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-6xl mx-auto space-y-8">
                <header className="flex items-start justify-between">
                    <div className="space-y-2">
                        <div className="flex items-center gap-3">
                            <div className="bg-sky-600/20 p-2 rounded-lg border border-sky-500/30">
                                <Activity className="w-6 h-6 text-sky-400" />
                            </div>
                            <h1 className="text-3xl font-bold tracking-tight">HTTP Performance Stats</h1>
                        </div>
                        <p className="text-neutral-400 text-sm ml-14">
                            Sliding-window (5 min) per-endpoint latency percentiles and error rates via ObservedAspect.
                        </p>
                    </div>
                    <div className="flex flex-col items-end gap-2">
                        <button
                            onClick={fetchData}
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

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                    <StatCard
                        icon={<TrendingUp className="w-4 h-4" />}
                        label="Total Requests"
                        value={loading ? "—" : (summary?.totalRequests ?? 0).toLocaleString()}
                        accent="border-sky-500/20"
                    />
                    <StatCard
                        icon={<AlertTriangle className="w-4 h-4" />}
                        label="Total Errors"
                        value={loading ? "—" : (summary?.totalErrors ?? 0).toLocaleString()}
                        accent="border-rose-500/20"
                    />
                    <StatCard
                        icon={<Zap className="w-4 h-4" />}
                        label="Endpoints Tracked"
                        value={loading ? "—" : summary?.trackedEndpoints ?? 0}
                        accent="border-violet-500/20"
                    />
                    <StatCard
                        icon={<Clock className="w-4 h-4" />}
                        label="Window"
                        value={loading ? "—" : `${(summary?.windowSeconds ?? 300) / 60}min`}
                        accent="border-emerald-500/20"
                    />
                </div>

                <div className="flex items-center gap-3">
                    <button
                        onClick={() => setErrorsOnly(false)}
                        className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition-colors ${!errorsOnly ? "bg-sky-600 text-white" : "bg-neutral-800 text-neutral-400 hover:bg-neutral-700"}`}
                    >
                        All Endpoints
                    </button>
                    <button
                        onClick={() => setErrorsOnly(true)}
                        className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition-colors ${errorsOnly ? "bg-rose-600 text-white" : "bg-neutral-800 text-neutral-400 hover:bg-neutral-700"}`}
                    >
                        Errors Only
                    </button>
                    <span className="text-xs text-neutral-600">Auto-refresh every 5s</span>
                </div>

                <section className="space-y-3">
                    {loading ? (
                        <div className="text-neutral-600 text-sm">Loading...</div>
                    ) : stats.length === 0 ? (
                        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-8 text-center text-neutral-600 text-sm">
                            No requests recorded yet. Make some API calls to see stats appear here.
                        </div>
                    ) : (
                        stats.map((stat) => (
                            <EndpointRow key={stat.endpoint} stat={stat} maxAvg={maxAvg} />
                        ))
                    )}
                </section>

                <section className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-3">
                    <h3 className="text-sm font-bold text-neutral-300 uppercase tracking-widest">How It Works</h3>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs text-neutral-500">
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">ObservedAspect</p>
                            <p>ObservedAspect Bean activates @Observed AOP. Any annotated method gets automatic Micrometer Observation wrapping.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">HttpMetricsFilter</p>
                            <p>OncePerRequestFilter records duration + HTTP status per normalized URI template into an in-memory ConcurrentLinkedDeque.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">Sliding window percentiles</p>
                            <p>HttpMetricsStore computes P50/P95/P99 over the last 5 minutes. Samples older than the window are purged lazily on read.</p>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    );
}
