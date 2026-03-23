"use client";

import { useState, useEffect, useCallback } from "react";
import { Webhook, Plus, Trash2, Send, RefreshCw, CheckCircle, XCircle, Clock, ChevronDown, ChevronUp } from "lucide-react";

const API_BASE = "http://localhost:8080";

interface WebhookEndpoint {
    id: number;
    targetUrl: string;
    eventTypes: string;
    active: boolean;
    createdAt: string;
}

interface DeliveryLog {
    id: number;
    endpointId: number;
    eventType: string;
    payload: string;
    status: "SUCCESS" | "FAILED" | "PENDING";
    httpStatus: number | null;
    responseBody: string | null;
    attemptCount: number;
    deliveredAt: string | null;
    createdAt: string;
}

function getAuthHeaders(): Record<string, string> {
    const token = localStorage.getItem("accessToken");
    return token
        ? { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }
        : { "Content-Type": "application/json" };
}

const EVENT_TYPES = [
    "APPLICATION_CREATED",
    "APPLICATION_STATUS_CHANGED",
];

function StatusBadge({ status }: { status: string }) {
    const map: Record<string, { color: string; icon: React.ReactNode }> = {
        SUCCESS: { color: "bg-emerald-500/15 text-emerald-400 border-emerald-500/30", icon: <CheckCircle className="w-3 h-3" /> },
        FAILED: { color: "bg-rose-500/15 text-rose-400 border-rose-500/30", icon: <XCircle className="w-3 h-3" /> },
        PENDING: { color: "bg-yellow-500/15 text-yellow-400 border-yellow-500/30", icon: <Clock className="w-3 h-3" /> },
    };
    const s = map[status] ?? map.PENDING;
    return (
        <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full border text-xs font-semibold ${s.color}`}>
            {s.icon}
            {status}
        </span>
    );
}

function PayloadViewer({ payload }: { payload: string }) {
    const [open, setOpen] = useState(false);
    let parsed: unknown;
    try { parsed = JSON.parse(payload); } catch { parsed = payload; }
    return (
        <div>
            <button
                onClick={() => setOpen(!open)}
                className="flex items-center gap-1 text-xs text-neutral-400 hover:text-neutral-200 transition-colors"
            >
                {open ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                {open ? "Hide payload" : "Show payload"}
            </button>
            {open && (
                <pre className="mt-2 p-3 bg-neutral-950 rounded-lg text-xs text-emerald-300 overflow-x-auto max-h-40">
                    {JSON.stringify(parsed, null, 2)}
                </pre>
            )}
        </div>
    );
}

export default function WebhooksPage() {
    const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
    const [logs, setLogs] = useState<DeliveryLog[]>([]);
    const [loading, setLoading] = useState(true);
    const [form, setForm] = useState({ targetUrl: "", secret: "", eventTypes: "APPLICATION_CREATED", active: true });
    const [formError, setFormError] = useState("");
    const [testEvent, setTestEvent] = useState("APPLICATION_CREATED");
    const [testResult, setTestResult] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<"endpoints" | "logs">("endpoints");

    const fetchAll = useCallback(async () => {
        try {
            const [epRes, logRes] = await Promise.all([
                fetch(`${API_BASE}/api/webhooks/endpoints`, { headers: getAuthHeaders() }),
                fetch(`${API_BASE}/api/webhooks/deliveries`, { headers: getAuthHeaders() }),
            ]);
            if (epRes.ok) setEndpoints(await epRes.json());
            if (logRes.ok) setLogs(await logRes.json());
        } catch {
            // silently fail if backend is unreachable
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchAll();
        const interval = setInterval(fetchAll, 10000);
        return () => clearInterval(interval);
    }, [fetchAll]);

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        setFormError("");
        if (!form.targetUrl.startsWith("http")) {
            setFormError("Target URL must start with http:// or https://");
            return;
        }
        try {
            const res = await fetch(`${API_BASE}/api/webhooks/endpoints`, {
                method: "POST",
                headers: getAuthHeaders(),
                body: JSON.stringify(form),
            });
            if (res.ok) {
                const ep = await res.json();
                setEndpoints((prev) => [ep, ...prev]);
                setForm({ targetUrl: "", secret: "", eventTypes: "APPLICATION_CREATED", active: true });
            } else {
                const err = await res.json();
                setFormError(err.message || "Registration failed");
            }
        } catch {
            setFormError("Network error");
        }
    };

    const handleDeactivate = async (id: number) => {
        try {
            const res = await fetch(`${API_BASE}/api/webhooks/endpoints/${id}`, {
                method: "DELETE",
                headers: getAuthHeaders(),
            });
            if (res.ok) {
                setEndpoints((prev) => prev.map((ep) => (ep.id === id ? { ...ep, active: false } : ep)));
            }
        } catch {
            // ignore
        }
    };

    const handleTestFire = async () => {
        setTestResult(null);
        try {
            const res = await fetch(`${API_BASE}/api/webhooks/test?eventType=${testEvent}`, {
                method: "POST",
                headers: getAuthHeaders(),
            });
            if (res.ok) {
                const data = await res.json();
                setTestResult(`Dispatched: ${data.eventType}`);
                setTimeout(fetchAll, 3000);
            } else {
                setTestResult("Failed to dispatch test event");
            }
        } catch {
            setTestResult("Network error");
        }
    };

    const successCount = logs.filter((l) => l.status === "SUCCESS").length;
    const failCount = logs.filter((l) => l.status === "FAILED").length;

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-6 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-6xl mx-auto space-y-8">

                {/* Header */}
                <header className="flex flex-col gap-2">
                    <div className="flex items-center gap-3">
                        <div className="bg-purple-600/20 p-2 rounded-lg border border-purple-500/30">
                            <Webhook className="w-6 h-6 text-purple-400" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold tracking-tight">Webhook Manager</h1>
                            <p className="text-neutral-400 text-sm">Register endpoints, verify HMAC signatures, track delivery history.</p>
                        </div>
                    </div>
                </header>

                {/* Stats row */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    {[
                        { label: "Endpoints", value: endpoints.length, color: "text-blue-400" },
                        { label: "Active", value: endpoints.filter((e) => e.active).length, color: "text-emerald-400" },
                        { label: "Deliveries OK", value: successCount, color: "text-emerald-400" },
                        { label: "Deliveries Failed", value: failCount, color: "text-rose-400" },
                    ].map((s) => (
                        <div key={s.label} className="bg-neutral-900 border border-neutral-800 rounded-xl p-4">
                            <p className="text-xs text-neutral-500 uppercase tracking-widest">{s.label}</p>
                            <p className={`text-3xl font-black mt-1 ${s.color}`}>{s.value}</p>
                        </div>
                    ))}
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">

                    {/* Registration form */}
                    <div className="lg:col-span-1 bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-5">
                        <h2 className="font-bold text-lg flex items-center gap-2">
                            <Plus className="w-4 h-4 text-purple-400" />
                            Register Endpoint
                        </h2>
                        <form onSubmit={handleRegister} className="space-y-4">
                            <div>
                                <label className="text-xs text-neutral-400 block mb-1">Target URL</label>
                                <input
                                    type="url"
                                    placeholder="https://yourapp.com/webhooks"
                                    value={form.targetUrl}
                                    onChange={(e) => setForm({ ...form, targetUrl: e.target.value })}
                                    className="w-full bg-neutral-800 border border-neutral-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-500 transition-colors"
                                    required
                                />
                            </div>
                            <div>
                                <label className="text-xs text-neutral-400 block mb-1">Secret (HMAC key)</label>
                                <input
                                    type="text"
                                    placeholder="your-signing-secret"
                                    value={form.secret}
                                    onChange={(e) => setForm({ ...form, secret: e.target.value })}
                                    className="w-full bg-neutral-800 border border-neutral-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-500 transition-colors"
                                    required
                                />
                            </div>
                            <div>
                                <label className="text-xs text-neutral-400 block mb-1">Event Types</label>
                                <select
                                    value={form.eventTypes}
                                    onChange={(e) => setForm({ ...form, eventTypes: e.target.value })}
                                    className="w-full bg-neutral-800 border border-neutral-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-500 transition-colors"
                                >
                                    {EVENT_TYPES.map((t) => (
                                        <option key={t} value={t}>{t}</option>
                                    ))}
                                    <option value="APPLICATION_CREATED,APPLICATION_STATUS_CHANGED">ALL EVENTS</option>
                                </select>
                            </div>
                            {formError && <p className="text-rose-400 text-xs">{formError}</p>}
                            <button
                                type="submit"
                                className="w-full bg-purple-600 hover:bg-purple-500 transition-colors rounded-lg py-2 text-sm font-semibold"
                            >
                                Register
                            </button>
                        </form>

                        <hr className="border-neutral-800" />

                        {/* Test fire */}
                        <div className="space-y-3">
                            <h3 className="text-sm font-bold flex items-center gap-2">
                                <Send className="w-3.5 h-3.5 text-yellow-400" />
                                Fire Test Event
                            </h3>
                            <select
                                value={testEvent}
                                onChange={(e) => setTestEvent(e.target.value)}
                                className="w-full bg-neutral-800 border border-neutral-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-yellow-500 transition-colors"
                            >
                                {EVENT_TYPES.map((t) => (
                                    <option key={t} value={t}>{t}</option>
                                ))}
                            </select>
                            <button
                                onClick={handleTestFire}
                                className="w-full bg-yellow-600 hover:bg-yellow-500 transition-colors rounded-lg py-2 text-sm font-semibold"
                            >
                                Fire
                            </button>
                            {testResult && (
                                <p className="text-xs text-emerald-400 bg-emerald-500/10 px-3 py-2 rounded-lg border border-emerald-500/20">
                                    {testResult}
                                </p>
                            )}
                        </div>
                    </div>

                    {/* Tabs: Endpoints & Logs */}
                    <div className="lg:col-span-2 bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-4">
                        <div className="flex items-center justify-between">
                            <div className="flex gap-1 bg-neutral-800 rounded-lg p-1">
                                {(["endpoints", "logs"] as const).map((tab) => (
                                    <button
                                        key={tab}
                                        onClick={() => setActiveTab(tab)}
                                        className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors capitalize ${activeTab === tab ? "bg-neutral-700 text-white" : "text-neutral-400 hover:text-neutral-200"}`}
                                    >
                                        {tab === "logs" ? "Delivery Logs" : "Endpoints"}
                                    </button>
                                ))}
                            </div>
                            <button
                                onClick={fetchAll}
                                className="p-2 rounded-lg hover:bg-neutral-800 transition-colors text-neutral-400 hover:text-white"
                                title="Refresh"
                            >
                                <RefreshCw className="w-4 h-4" />
                            </button>
                        </div>

                        {loading ? (
                            <div className="flex items-center justify-center h-40 text-neutral-500">
                                <RefreshCw className="w-5 h-5 animate-spin mr-2" />
                                Loading...
                            </div>
                        ) : activeTab === "endpoints" ? (
                            <div className="space-y-3 max-h-[520px] overflow-y-auto pr-1">
                                {endpoints.length === 0 ? (
                                    <p className="text-neutral-500 text-sm text-center py-12">No endpoints registered yet.</p>
                                ) : (
                                    endpoints.map((ep) => (
                                        <div
                                            key={ep.id}
                                            className={`border rounded-xl p-4 space-y-2 transition-colors ${ep.active ? "border-neutral-700 hover:border-neutral-600" : "border-neutral-800 opacity-60"}`}
                                        >
                                            <div className="flex items-start justify-between gap-2">
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-sm font-mono text-purple-300 truncate">{ep.targetUrl}</p>
                                                    <p className="text-xs text-neutral-500 mt-0.5">
                                                        Events: <span className="text-neutral-300">{ep.eventTypes}</span>
                                                    </p>
                                                </div>
                                                <div className="flex items-center gap-2 flex-shrink-0">
                                                    <span className={`text-xs px-2 py-0.5 rounded-full border font-semibold ${ep.active ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30" : "bg-neutral-700 text-neutral-400 border-neutral-600"}`}>
                                                        {ep.active ? "ACTIVE" : "INACTIVE"}
                                                    </span>
                                                    {ep.active && (
                                                        <button
                                                            onClick={() => handleDeactivate(ep.id)}
                                                            className="p-1.5 rounded-lg hover:bg-rose-500/20 text-neutral-500 hover:text-rose-400 transition-colors"
                                                            title="Deactivate"
                                                        >
                                                            <Trash2 className="w-3.5 h-3.5" />
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                            <p className="text-xs text-neutral-600">
                                                Created: {new Date(ep.createdAt).toLocaleString()}
                                            </p>
                                        </div>
                                    ))
                                )}
                            </div>
                        ) : (
                            <div className="space-y-3 max-h-[520px] overflow-y-auto pr-1">
                                {logs.length === 0 ? (
                                    <p className="text-neutral-500 text-sm text-center py-12">No delivery logs yet.</p>
                                ) : (
                                    logs.map((log) => (
                                        <div key={log.id} className="border border-neutral-800 hover:border-neutral-700 rounded-xl p-4 space-y-2 transition-colors">
                                            <div className="flex items-center justify-between flex-wrap gap-2">
                                                <div className="flex items-center gap-2">
                                                    <StatusBadge status={log.status} />
                                                    <span className="text-xs font-mono text-neutral-300">{log.eventType}</span>
                                                </div>
                                                <div className="flex items-center gap-3 text-xs text-neutral-500">
                                                    {log.httpStatus && (
                                                        <span className={log.httpStatus >= 200 && log.httpStatus < 300 ? "text-emerald-400" : "text-rose-400"}>
                                                            HTTP {log.httpStatus}
                                                        </span>
                                                    )}
                                                    <span>Attempts: {log.attemptCount}</span>
                                                    <span>Endpoint #{log.endpointId}</span>
                                                </div>
                                            </div>
                                            <PayloadViewer payload={log.payload} />
                                            <p className="text-xs text-neutral-600">
                                                {new Date(log.createdAt).toLocaleString()}
                                            </p>
                                        </div>
                                    ))
                                )}
                            </div>
                        )}
                    </div>
                </div>

                {/* Signature info panel */}
                <div className="bg-neutral-900/60 border border-neutral-800 rounded-2xl p-6">
                    <h3 className="font-bold mb-3 text-sm text-neutral-300">How to verify webhook signatures</h3>
                    <pre className="text-xs text-emerald-300 bg-neutral-950 rounded-lg p-4 overflow-x-auto leading-relaxed">
{`// Node.js example
const crypto = require('crypto');

app.post('/webhook', (req, res) => {
  const sig = req.headers['x-webhook-signature'];  // "sha256=<hex>"
  const body = JSON.stringify(req.body);

  const expected = 'sha256=' + crypto
    .createHmac('sha256', YOUR_SECRET)
    .update(body)
    .digest('hex');

  if (sig !== expected) return res.status(401).send('Invalid signature');
  // Process event...
  res.status(200).send('OK');
});`}
                    </pre>
                </div>

            </div>
        </div>
    );
}
