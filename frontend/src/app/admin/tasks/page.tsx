"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/axios";
import { 
    LayoutDashboard, 
    Play, 
    CheckCircle2, 
    Clock, 
    AlertCircle, 
    Cpu, 
    HardDrive,
    Search,
    RefreshCw,
    Terminal,
    Settings,
    History
} from "lucide-react";
import { useState } from "react";

interface BatchJob {
    instanceId: number;
    jobName: string;
    status: string;
    exitStatus: string;
    startTime: string | null;
    endTime: string | null;
}

export default function TaskCenterPage() {
    const queryClient = useQueryClient();
    const [search, setSearch] = useState("");

    const { data: batchJobs, isLoading: loadingBatch } = useQuery<BatchJob[]>({
        queryKey: ["batch-jobs"],
        queryFn: async () => (await api.get("/api/admin/batch/jobs")).data,
        refetchInterval: 5000,
    });

    const runJobMutation = useMutation({
        mutationFn: async (jobName: string) => api.post(`/api/admin/batch/run?jobName=${jobName}`),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["batch-jobs"] });
        },
    });

    const getStatusColor = (status: string) => {
        switch (status) {
            case "COMPLETED": return "text-emerald-500 bg-emerald-500/10 border-emerald-500/20";
            case "STARTED": return "text-blue-500 bg-blue-500/10 border-blue-500/20 animate-pulse";
            case "FAILED": return "text-rose-500 bg-rose-500/10 border-rose-500/20";
            default: return "text-neutral-500 bg-neutral-500/10 border-neutral-500/20";
        }
    };

    const filteredJobs = batchJobs?.filter(job => 
        job.jobName.toLowerCase().includes(search.toLowerCase()) || 
        job.status.toLowerCase().includes(search.toLowerCase())
    );

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-[1400px] mx-auto space-y-10">
                {/* Header */}
                <header className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
                    <div className="space-y-1">
                        <div className="flex items-center gap-3">
                            <div className="bg-indigo-600/20 p-2 rounded-xl border border-indigo-500/30">
                                <LayoutDashboard className="w-6 h-6 text-indigo-500" />
                            </div>
                            <h1 className="text-3xl font-black tracking-tighter italic uppercase text-white">
                                Task Center
                            </h1>
                        </div>
                        <p className="text-neutral-500 text-sm font-medium">Distributed Batch Management & Optimization Intel.</p>
                    </div>

                    <div className="flex items-center gap-3">
                        <div className="relative group">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-600 group-focus-within:text-indigo-500" />
                            <input 
                                type="text"
                                placeholder="Search jobs..."
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                className="pl-10 pr-4 py-2 bg-neutral-900/50 border border-neutral-800 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500/50 w-64 transition-all"
                            />
                        </div>
                        <button className="p-2.5 bg-neutral-900 border border-neutral-800 rounded-xl hover:border-neutral-700 transition-colors">
                            <Settings className="w-5 h-5 text-neutral-400" />
                        </button>
                    </div>
                </header>

                {/* Computational Intel */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                    <div className="bg-neutral-900/40 border border-neutral-800/50 p-6 rounded-2xl">
                        <div className="flex items-center gap-3 mb-4">
                            <Cpu className="w-5 h-5 text-indigo-500" />
                            <span className="text-xs font-bold uppercase tracking-widest text-neutral-500">OCR Cache Hit</span>
                        </div>
                        <div className="flex items-baseline gap-2">
                            <h3 className="text-3xl font-black text-white">92.4%</h3>
                            <span className="text-xs text-emerald-500 font-bold">+5.2%</span>
                        </div>
                        <div className="mt-4 h-1.5 w-full bg-neutral-800 rounded-full overflow-hidden">
                            <div className="h-full bg-indigo-500 rounded-full" style={{ width: '92.4%' }} />
                        </div>
                    </div>
                    <div className="bg-neutral-900/40 border border-neutral-800/50 p-6 rounded-2xl">
                        <div className="flex items-center gap-3 mb-4">
                            <HardDrive className="w-5 h-5 text-emerald-500" />
                            <span className="text-xs font-bold uppercase tracking-widest text-neutral-500">Compute Saved</span>
                        </div>
                        <h3 className="text-3xl font-black text-white">124h / mo</h3>
                        <p className="text-[10px] text-neutral-600 mt-2">Estimated Tesseract CPU cycles bypassed.</p>
                    </div>
                    <div className="bg-neutral-900/40 border border-neutral-800/50 p-6 rounded-2xl">
                        <div className="flex items-center gap-3 mb-4">
                            <RefreshCw className="w-5 h-5 text-amber-500" />
                            <span className="text-xs font-bold uppercase tracking-widest text-neutral-500">Idempotent Replay</span>
                        </div>
                        <h3 className="text-3xl font-black text-white">1,402</h3>
                        <p className="text-[10px] text-neutral-600 mt-2">Redundant calls served from Redis.</p>
                    </div>
                    <div className="bg-neutral-900/40 border border-neutral-800/50 p-6 rounded-2xl">
                        <div className="flex items-center gap-3 mb-4">
                            <Terminal className="w-5 h-5 text-rose-500" />
                            <span className="text-xs font-bold uppercase tracking-widest text-neutral-500">Active Batch</span>
                        </div>
                        <h3 className="text-3xl font-black text-white">
                            {batchJobs?.filter(j => j.status === 'STARTED').length || 0}
                        </h3>
                        <p className="text-[10px] text-neutral-600 mt-2">Current parallel job executions.</p>
                    </div>
                </div>

                {/* Batch Jobs Table */}
                <section className="space-y-6">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <History className="w-5 h-5 text-indigo-500" />
                            <h2 className="text-xl font-black uppercase tracking-tight">Job Execution History</h2>
                        </div>
                        <button 
                            onClick={() => runJobMutation.mutate("techTrendJob")}
                            disabled={runJobMutation.isPending}
                            className="bg-indigo-600 hover:bg-indigo-700 disabled:bg-neutral-800 text-white px-5 py-2 rounded-xl text-sm font-bold flex items-center gap-2 transition-all active:scale-95"
                        >
                            <Play className="w-4 h-4 fill-current" />
                            Run Tech Trend Job
                        </button>
                    </div>

                    <div className="bg-neutral-900/30 border border-neutral-800 rounded-2xl overflow-hidden backdrop-blur-sm">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left">
                                <thead className="bg-neutral-900/50 border-b border-neutral-800">
                                    <tr>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase text-neutral-500 tracking-wider">ID / Name</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase text-neutral-500 tracking-wider">Status</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase text-neutral-500 tracking-wider">Time Range</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase text-neutral-500 tracking-wider">Exit Status</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase text-neutral-500 tracking-wider text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-neutral-800/50">
                                    {filteredJobs?.map((job) => (
                                        <tr key={job.instanceId} className="hover:bg-indigo-500/5 transition-colors group">
                                            <td className="px-6 py-4">
                                                <div className="flex flex-col">
                                                    <span className="font-bold text-white text-sm">{job.jobName}</span>
                                                    <span className="text-neutral-600 text-[10px] font-mono">#{job.instanceId}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className={`px-2.5 py-1 rounded-lg border text-[10px] font-black uppercase ${getStatusColor(job.status)}`}>
                                                    {job.status}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 text-xs text-neutral-400">
                                                <div className="flex flex-col gap-1">
                                                    <div className="flex items-center gap-2">
                                                        <Clock className="w-3.5 h-3.5" />
                                                        {job.startTime ? new Date(job.startTime).toLocaleString() : 'N/A'}
                                                    </div>
                                                    {job.endTime && (
                                                        <div className="flex items-center gap-2 text-neutral-600 ml-1 border-l border-neutral-800 pl-3">
                                                            {new Date(job.endTime).toLocaleString()}
                                                        </div>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className={`font-mono text-[10px] font-bold ${job.exitStatus === 'COMPLETED' ? 'text-neutral-400' : 'text-rose-500'}`}>
                                                    {job.exitStatus}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <button className="p-2 hover:bg-neutral-800 rounded-lg transition-colors text-neutral-500 hover:text-white">
                                                    <Search className="w-4 h-4" />
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    {(!filteredJobs || filteredJobs.length === 0) && (
                                        <tr>
                                            <td colSpan={5} className="px-6 py-12 text-center text-neutral-600 italic">
                                                No job execution history found.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    );
}
