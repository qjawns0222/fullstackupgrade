"use client";

import { useState, useEffect } from "react";
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    AreaChart,
    Area,
} from "recharts";
import { Activity, ShieldCheck, ShieldAlert, Cpu } from "lucide-react";

interface ResilienceMetric {
    name: string;
    state: string;
    failureRate: number;
    slowCallRate: number;
    bufferedCalls: number;
    timestamp: string;
}

export default function MonitoringPage() {
    const [metrics, setMetrics] = useState<ResilienceMetric[]>([]);
    const [history, setHistory] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);

    const fetchMetrics = async () => {
        try {
            // In a real scenario, we'd fetch from Actuator
            // For this project, we'll simulate the Actuator data structure based on the config
            // const res = await fetch("http://localhost:8000/actuator/health");
            
            // Simulation for UI demonstration
            const mockServices = ["s3Service", "ocrService"];
            const newMetrics = mockServices.map((name) => ({
                name,
                state: Math.random() > 0.9 ? "OPEN" : "CLOSED",
                failureRate: Math.floor(Math.random() * 20),
                slowCallRate: Math.floor(Math.random() * 10),
                bufferedCalls: Math.floor(Math.random() * 100),
                timestamp: new Date().toLocaleTimeString(),
            }));

            setMetrics(newMetrics);
            setHistory((prev) => [...prev, ...newMetrics].slice(-20)); // Keep last 20 points
            setLoading(false);
        } catch (error) {
            console.error("Failed to fetch monitoring data", error);
        }
    };

    useEffect(() => {
        const interval = setInterval(fetchMetrics, 3000); // Polling every 3 seconds
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-7xl mx-auto space-y-8">
                {/* Header */}
                <header className="flex flex-col gap-2">
                    <div className="flex items-center gap-3">
                        <div className="bg-blue-600/20 p-2 rounded-lg border border-blue-500/30">
                            <Activity className="w-6 h-6 text-blue-500" />
                        </div>
                        <h1 className="text-3xl font-bold tracking-tight">System Resilience Monitor</h1>
                    </div>
                    <p className="text-neutral-400 text-sm">Real-time observability into backend circuit breakers and integration health.</p>
                </header>

                {/* KPI Cards */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    {metrics.map((m) => (
                        <div key={m.name} className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-4 hover:border-neutral-700 transition-colors shadow-2xl">
                            <div className="flex justify-between items-start">
                                <div className="space-y-1">
                                    <span className="text-xs font-bold text-neutral-500 uppercase tracking-widest">{m.name}</span>
                                    <div className="flex items-center gap-2">
                                        <div className={`w-2 h-2 rounded-full animate-pulse ${m.state === "CLOSED" ? "bg-emerald-500" : "bg-rose-500"}`} />
                                        <h2 className="text-xl font-bold">{m.state}</h2>
                                    </div>
                                </div>
                                {m.state === "CLOSED" ? (
                                    <ShieldCheck className="w-8 h-8 text-emerald-500/50" />
                                ) : (
                                    <ShieldAlert className="w-8 h-8 text-rose-500/50" />
                                )}
                            </div>
                            
                            <div className="grid grid-cols-2 gap-4 pt-2">
                                <div className="space-y-1">
                                    <span className="text-[10px] text-neutral-500 uppercase">Failure Rate</span>
                                    <p className={`text-lg font-mono font-bold ${m.failureRate > 10 ? "text-rose-400" : "text-emerald-400"}`}>{m.failureRate}%</p>
                                </div>
                                <div className="space-y-1">
                                    <span className="text-[10px] text-neutral-500 uppercase">Buffer size</span>
                                    <p className="text-lg font-mono font-bold text-blue-400">{m.bufferedCalls}</p>
                                </div>
                            </div>

                            <div className="w-full bg-neutral-800 h-1.5 rounded-full overflow-hidden">
                                <div 
                                    className={`h-full transition-all duration-1000 ${m.state === "CLOSED" ? "bg-emerald-500/50" : "bg-rose-500/50"}`} 
                                    style={{ width: `${100 - m.failureRate}%` }}
                                />
                            </div>
                        </div>
                    ))}
                    
                    <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 space-y-4 shadow-2xl flex flex-col justify-center">
                        <div className="flex items-center gap-3">
                            <Cpu className="w-6 h-6 text-orange-500" />
                            <span className="text-sm font-bold text-neutral-300">OCR Bulkhead</span>
                        </div>
                        <p className="text-2xl font-black text-neutral-100 italic tracking-tighter">MAX 3 CONCURRENT</p>
                        <p className="text-neutral-500 text-xs">Isolating compute-heavy OCR tasks to prevent system-wide thread starvation.</p>
                    </div>
                </div>

                {/* Charts */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                    <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-8 space-y-6 shadow-2xl">
                        <h3 className="text-lg font-bold flex items-center gap-2">
                            <span className="w-1.5 h-6 bg-blue-600 rounded-full" />
                            Failure Rate Trends
                        </h3>
                        <div className="h-[300px] w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={history.filter(h => h.name === "s3Service")}>
                                    <defs>
                                        <linearGradient id="colorFail" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                                            <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#262626" vertical={false} />
                                    <XAxis dataKey="timestamp" hide />
                                    <YAxis stroke="#525252" fontSize={12} unit="%" />
                                    <Tooltip 
                                        contentStyle={{ backgroundColor: "#171717", border: "1px solid #262626", borderRadius: "12px" }}
                                        labelStyle={{ display: "none" }}
                                    />
                                    <Area type="monotone" dataKey="failureRate" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorFail)" />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    </div>

                    <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-8 space-y-6 shadow-2xl">
                        <h3 className="text-lg font-bold flex items-center gap-2">
                            <span className="w-1.5 h-6 bg-orange-600 rounded-full" />
                            Slow Call Detection (OCR)
                        </h3>
                        <div className="h-[300px] w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={history.filter(h => h.name === "ocrService")}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#262626" vertical={false} />
                                    <XAxis dataKey="timestamp" hide />
                                    <YAxis stroke="#525252" fontSize={12} />
                                    <Tooltip 
                                        contentStyle={{ backgroundColor: "#171717", border: "1px solid #262626", borderRadius: "12px" }}
                                    />
                                    <Line type="stepAfter" dataKey="slowCallRate" stroke="#f97316" strokeWidth={3} dot={false} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
