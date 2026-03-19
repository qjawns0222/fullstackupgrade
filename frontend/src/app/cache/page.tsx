"use client";

import { useState, useEffect, useCallback } from "react";
import { Database, Zap, Trash2, RefreshCw, Server, Layers } from "lucide-react";

interface CacheStatsSnapshot {
    cacheName: string;
    l1EstimatedSize: number;
    l1HitCount: number;
    l1MissCount: number;
    l1HitRate: number;
}

type StatsMap = Record<string, CacheStatsSnapshot>;

const API_BASE = "http://localhost:8080";

export default function CacheMonitorPage() {
    const [stats, setStats] = useState<StatsMap>({});
    const [loading, setLoading] = useState(false);
    const [lastUpdated, setLastUpdated] = useState<string>("");
    const [message, setMessage] = useState<{ text: string; type: "success" | "error" } | null>(null);

    const fetchStats = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch(`${API_BASE}/api/cache/stats`);
            if (res.ok) {
                const data: StatsMap = await res.json();
                setStats(data);
                setLastUpdated(new Date().toLocaleTimeString());
            }
        } catch {
            // backend may not be running; show empty state
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchStats();
        const interval = setInterval(fetchStats, 5000);
        return () => clearInterval(interval);
    }, [fetchStats]);

    const clearCache = async (cacheName: string) => {
        try {
            const res = await fetch(`${API_BASE}/api/cache/${cacheName}`, { method: "DELETE" });
            const data = await res.json();
            if (res.ok) {
                setMessage({ text: data.message, type: "success" });
                fetchStats();
            } else {
                setMessage({ text: "Failed to clear cache", type: "error" });
            }
        } catch {
            setMessage({ text: "Could not reach backend", type: "error" });
        }
        setTimeout(() => setMessage(null), 3000);
    };

    const hitRateColor = (rate: number) => {
        if (rate >= 0.8) return "text-emerald-400";
        if (rate >= 0.5) return "text-yellow-400";
        return "text-rose-400";
    };

    const hitRateBar = (rate: number) => {
        if (rate >= 0.8) return "bg-emerald-500";
        if (rate >= 0.5) return "bg-yellow-500";
        return "bg-rose-500";
    };

    const statsEntries = Object.entries(stats);

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-7xl mx-auto space-y-8">

                {/* Header */}
                <header className="flex flex-col gap-3">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="bg-violet-600/20 p-2 rounded-lg border border-violet-500/30">
                                <Layers className="w-6 h-6 text-violet-400" />
                            </div>
                            <div>
                                <h1 className="text-3xl font-bold tracking-tight">Two-Level Cache Monitor</h1>
                                <p className="text-neutral-400 text-sm mt-0.5">
                                    L1 Caffeine (JVM-local) + L2 Redis — cluster-wide invalidation via pub/sub
                                </p>
                            </div>
                        </div>
                        <button
                            onClick={fetchStats}
                            disabled={loading}
                            className="flex items-center gap-2 bg-neutral-800 hover:bg-neutral-700 border border-neutral-700 px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                        >
                            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
                            Refresh
                        </button>
                    </div>
                    {lastUpdated && (
                        <p className="text-xs text-neutral-500">Last updated: {lastUpdated}</p>
                    )}
                </header>

                {/* Toast message */}
                {message && (
                    <div className={`px-4 py-3 rounded-lg text-sm font-medium border ${
                        message.type === "success"
                            ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-400"
                            : "bg-rose-500/10 border-rose-500/30 text-rose-400"
                    }`}>
                        {message.text}
                    </div>
                )}

                {/* Architecture info */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 space-y-2">
                        <div className="flex items-center gap-2 text-violet-400">
                            <Zap className="w-4 h-4" />
                            <span className="text-xs font-bold uppercase tracking-widest">L1 — Caffeine</span>
                        </div>
                        <p className="text-2xl font-black">JVM Local</p>
                        <p className="text-neutral-500 text-xs">Sub-millisecond reads. Max 200 entries per region. TTL: 30s.</p>
                    </div>
                    <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 space-y-2">
                        <div className="flex items-center gap-2 text-blue-400">
                            <Database className="w-4 h-4" />
                            <span className="text-xs font-bold uppercase tracking-widest">L2 — Redis</span>
                        </div>
                        <p className="text-2xl font-black">Distributed</p>
                        <p className="text-neutral-500 text-xs">Shared across all nodes. Serialized JSON. TTL: 300s.</p>
                    </div>
                    <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 space-y-2">
                        <div className="flex items-center gap-2 text-orange-400">
                            <Server className="w-4 h-4" />
                            <span className="text-xs font-bold uppercase tracking-widest">Invalidation</span>
                        </div>
                        <p className="text-2xl font-black">Pub/Sub</p>
                        <p className="text-neutral-500 text-xs">Redis channel "cache:invalidation" broadcasts eviction to all JVM nodes instantly.</p>
                    </div>
                </div>

                {/* Cache region stats */}
                <div className="space-y-4">
                    <h2 className="text-lg font-bold text-neutral-200">Cache Regions</h2>

                    {statsEntries.length === 0 ? (
                        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-12 text-center">
                            <Layers className="w-12 h-12 text-neutral-700 mx-auto mb-3" />
                            <p className="text-neutral-500 text-sm">
                                No active cache regions yet. Regions are created lazily on first access.<br/>
                                Start using the application to see L1 hit/miss statistics here.
                            </p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            {statsEntries.map(([name, snap]) => {
                                const total = snap.l1HitCount + snap.l1MissCount;
                                const pct = Math.round(snap.l1HitRate * 100);
                                return (
                                    <div key={name} className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-5 hover:border-neutral-700 transition-colors">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <span className="text-xs font-bold text-neutral-500 uppercase tracking-widest">{name}</span>
                                                <p className={`text-3xl font-black mt-1 ${hitRateColor(snap.l1HitRate)}`}>
                                                    {pct}%
                                                </p>
                                                <p className="text-xs text-neutral-500">L1 Hit Rate</p>
                                            </div>
                                            <button
                                                onClick={() => clearCache(name)}
                                                className="flex items-center gap-1.5 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-400 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors"
                                            >
                                                <Trash2 className="w-3 h-3" />
                                                Clear
                                            </button>
                                        </div>

                                        <div className="w-full bg-neutral-800 h-2 rounded-full overflow-hidden">
                                            <div
                                                className={`h-full transition-all duration-700 ${hitRateBar(snap.l1HitRate)}`}
                                                style={{ width: `${pct}%` }}
                                            />
                                        </div>

                                        <div className="grid grid-cols-3 gap-3">
                                            <div className="bg-neutral-800/50 rounded-xl p-3">
                                                <span className="text-[10px] text-neutral-500 uppercase block">L1 Size</span>
                                                <p className="text-lg font-bold font-mono text-neutral-100">{snap.l1EstimatedSize}</p>
                                            </div>
                                            <div className="bg-neutral-800/50 rounded-xl p-3">
                                                <span className="text-[10px] text-neutral-500 uppercase block">Hits</span>
                                                <p className="text-lg font-bold font-mono text-emerald-400">{snap.l1HitCount}</p>
                                            </div>
                                            <div className="bg-neutral-800/50 rounded-xl p-3">
                                                <span className="text-[10px] text-neutral-500 uppercase block">Misses</span>
                                                <p className="text-lg font-bold font-mono text-rose-400">{snap.l1MissCount}</p>
                                            </div>
                                        </div>

                                        {total > 0 && (
                                            <p className="text-xs text-neutral-600">
                                                {total.toLocaleString()} total L1 lookups — {snap.l1HitCount.toLocaleString()} saved Redis round-trips
                                            </p>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
