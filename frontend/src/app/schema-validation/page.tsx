"use client";

import { useState, useEffect, useCallback } from "react";
import { ShieldAlert, BarChart2, Trash2, RefreshCw, FileJson, AlertCircle } from "lucide-react";

interface SchemaViolation {
    id: string;
    schemaPath: string;
    endpoint: string;
    method: string;
    violations: string[];
    requestPayload: string;
    occurredAt: string;
}

interface ViolationStats {
    total: number;
    bySchema: Record<string, number>;
    byEndpoint: Record<string, number>;
}

const API_BASE = "http://localhost:8080";

function Badge({ label, variant }: { label: string; variant: "danger" | "warning" | "info" | "neutral" }) {
    const colors = {
        danger: "bg-rose-500/15 text-rose-400 border border-rose-500/30",
        warning: "bg-amber-500/15 text-amber-400 border border-amber-500/30",
        info: "bg-blue-500/15 text-blue-400 border border-blue-500/30",
        neutral: "bg-neutral-700/50 text-neutral-400 border border-neutral-600/30",
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

function ViolationRow({ violation }: { violation: SchemaViolation }) {
    const [expanded, setExpanded] = useState(false);
    let payload: string;
    try {
        payload = JSON.stringify(JSON.parse(violation.requestPayload), null, 2);
    } catch {
        payload = violation.requestPayload;
    }

    return (
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-3 hover:border-rose-500/30 transition-colors">
            <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-2 flex-wrap">
                    <Badge label={violation.method} variant="neutral" />
                    <code className="text-sm text-rose-300 font-mono">{violation.endpoint}</code>
                </div>
                <Badge label={violation.schemaPath.split("/").pop() || violation.schemaPath} variant="warning" />
            </div>
            <ul className="space-y-1">
                {violation.violations.map((v, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-neutral-400">
                        <AlertCircle className="w-3 h-3 text-rose-500 mt-0.5 shrink-0" />
                        <span className="font-mono">{v}</span>
                    </li>
                ))}
            </ul>
            <div className="flex items-center justify-between">
                <p className="text-[11px] text-neutral-600">{new Date(violation.occurredAt).toLocaleString()}</p>
                <button
                    onClick={() => setExpanded(!expanded)}
                    className="text-[11px] text-neutral-500 hover:text-neutral-300 transition-colors"
                >
                    {expanded ? "Hide payload" : "Show payload"}
                </button>
            </div>
            {expanded && (
                <pre className="text-[11px] font-mono text-neutral-400 bg-neutral-950 border border-neutral-800 rounded-lg p-3 overflow-x-auto max-h-40">
                    {payload}
                </pre>
            )}
        </div>
    );
}

function DistributionBar({ label, count, total }: { label: string; count: number; total: number }) {
    const pct = total > 0 ? Math.round((count / total) * 100) : 0;
    return (
        <div className="space-y-1">
            <div className="flex items-center justify-between text-xs">
                <span className="font-mono text-neutral-300 truncate max-w-[70%]">{label}</span>
                <span className="text-neutral-500">{count} ({pct}%)</span>
            </div>
            <div className="h-1.5 bg-neutral-800 rounded-full overflow-hidden">
                <div
                    className="h-full bg-rose-500/70 rounded-full transition-all duration-500"
                    style={{ width: `${pct}%` }}
                />
            </div>
        </div>
    );
}

export default function SchemaValidationPage() {
    const [violations, setViolations] = useState<SchemaViolation[]>([]);
    const [stats, setStats] = useState<ViolationStats | null>(null);
    const [loading, setLoading] = useState(true);
    const [lastRefresh, setLastRefresh] = useState<string>("");
    const [error, setError] = useState<string | null>(null);
    const [clearing, setClearing] = useState(false);

    const fetchData = useCallback(async () => {
        try {
            const [violRes, statsRes] = await Promise.all([
                fetch(`${API_BASE}/api/schema-validation/violations?limit=50`),
                fetch(`${API_BASE}/api/schema-validation/stats`),
            ]);
            if (!violRes.ok || !statsRes.ok) throw new Error(`HTTP ${violRes.status}`);
            const [v, s] = await Promise.all([violRes.json(), statsRes.json()]);
            setViolations(v);
            setStats(s);
            setLastRefresh(new Date().toLocaleTimeString());
            setError(null);
        } catch (e: any) {
            setError(e.message || "Failed to fetch");
        } finally {
            setLoading(false);
        }
    }, []);

    const clearViolations = async () => {
        setClearing(true);
        try {
            await fetch(`${API_BASE}/api/schema-validation/violations`, { method: "DELETE" });
            await fetchData();
        } finally {
            setClearing(false);
        }
    };

    useEffect(() => {
        fetchData();
        const interval = setInterval(fetchData, 5000);
        return () => clearInterval(interval);
    }, [fetchData]);

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-6xl mx-auto space-y-8">
                {/* Header */}
                <header className="flex items-start justify-between">
                    <div className="space-y-2">
                        <div className="flex items-center gap-3">
                            <div className="bg-rose-600/20 p-2 rounded-lg border border-rose-500/30">
                                <ShieldAlert className="w-6 h-6 text-rose-400" />
                            </div>
                            <h1 className="text-3xl font-bold tracking-tight">JSON Schema Validation</h1>
                        </div>
                        <p className="text-neutral-400 text-sm ml-14">
                            API contract enforcement — real-time violation tracking via networknt/json-schema-validator.
                        </p>
                    </div>
                    <div className="flex flex-col items-end gap-2">
                        <div className="flex items-center gap-2">
                            <button
                                onClick={clearViolations}
                                disabled={clearing}
                                className="flex items-center gap-2 px-3 py-2 rounded-lg bg-neutral-800 hover:bg-rose-900/40 text-sm text-neutral-300 hover:text-rose-300 transition-colors border border-neutral-700 hover:border-rose-700/40 disabled:opacity-50"
                            >
                                <Trash2 className="w-3.5 h-3.5" />
                                Clear
                            </button>
                            <button
                                onClick={fetchData}
                                className="flex items-center gap-2 px-3 py-2 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-sm text-neutral-300 transition-colors border border-neutral-700"
                            >
                                <RefreshCw className="w-3.5 h-3.5" />
                                Refresh
                            </button>
                        </div>
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
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
                    <StatCard
                        icon={<ShieldAlert className="w-4 h-4" />}
                        label="Total Violations"
                        value={loading ? "—" : stats?.total ?? 0}
                        accent="border-rose-500/20"
                    />
                    <StatCard
                        icon={<FileJson className="w-4 h-4" />}
                        label="Schemas Enforced"
                        value={loading ? "—" : Object.keys(stats?.bySchema ?? {}).length}
                        accent="border-blue-500/20"
                    />
                    <StatCard
                        icon={<BarChart2 className="w-4 h-4" />}
                        label="Affected Endpoints"
                        value={loading ? "—" : Object.keys(stats?.byEndpoint ?? {}).length}
                        accent="border-amber-500/20"
                    />
                </div>

                {/* Distribution */}
                {stats && stats.total > 0 && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-4">
                            <h3 className="text-sm font-bold text-neutral-300 uppercase tracking-widest">By Schema</h3>
                            <div className="space-y-3">
                                {Object.entries(stats.bySchema)
                                    .sort(([, a], [, b]) => b - a)
                                    .map(([schema, count]) => (
                                        <DistributionBar key={schema} label={schema} count={count} total={stats.total} />
                                    ))}
                            </div>
                        </div>
                        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-4">
                            <h3 className="text-sm font-bold text-neutral-300 uppercase tracking-widest">By Endpoint</h3>
                            <div className="space-y-3">
                                {Object.entries(stats.byEndpoint)
                                    .sort(([, a], [, b]) => b - a)
                                    .map(([endpoint, count]) => (
                                        <DistributionBar key={endpoint} label={endpoint} count={count} total={stats.total} />
                                    ))}
                            </div>
                        </div>
                    </div>
                )}

                {/* Violations */}
                <section className="space-y-4">
                    <div className="flex items-center gap-3">
                        <span className="w-1.5 h-6 bg-rose-500 rounded-full" />
                        <h2 className="text-lg font-bold">Recent Violations</h2>
                        <span className="text-xs text-neutral-500">(latest 50, auto-refresh every 5s)</span>
                    </div>
                    {loading ? (
                        <div className="text-neutral-600 text-sm">Loading...</div>
                    ) : violations.length > 0 ? (
                        <div className="space-y-3">
                            {violations.map((v) => (
                                <ViolationRow key={v.id} violation={v} />
                            ))}
                        </div>
                    ) : (
                        <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-8 text-center text-neutral-600 text-sm">
                            No schema violations recorded. All API requests conform to their JSON Schema contracts.
                        </div>
                    )}
                </section>

                {/* Architecture Note */}
                <section className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-3">
                    <h3 className="text-sm font-bold text-neutral-300 uppercase tracking-widest">How It Works</h3>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs text-neutral-500">
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">@ValidateJsonSchema AOP</p>
                            <p>An AspectJ @Around advice intercepts controller methods annotated with @ValidateJsonSchema and validates the @RequestBody against the declared schema.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">networknt json-schema-validator</p>
                            <p>Schemas are loaded from classpath, cached in-memory, and validated using Draft-7 JSON Schema. Supports if/then/else, oneOf, and conditional required rules.</p>
                        </div>
                        <div className="space-y-1">
                            <p className="font-semibold text-neutral-400">SchemaViolationStore</p>
                            <p>Violations are stored in a thread-safe ConcurrentLinkedDeque (max 500 entries). Stats are computed on-the-fly for real-time dashboard visibility.</p>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    );
}
