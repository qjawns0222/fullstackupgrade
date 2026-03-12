"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/axios";
import { 
    Activity, 
    RefreshCcw, 
    CheckCircle2, 
    AlertCircle, 
    Clock, 
    Database, 
    Zap,
    ChevronRight,
    Search
} from "lucide-react";
import { useState } from "react";

interface EventPublication {
    id: string;
    eventType: string;
    listenerId: string;
    publicationDate: string;
    completionDate: string | null;
    eventPayload: string;
}

export default function EventDashboard() {
    const queryClient = useQueryClient();
    const [search, setSearch] = useState("");

    const { data: incomplete, isLoading: loadingInc } = useQuery<EventPublication[]>({
        queryKey: ["events-incomplete"],
        queryFn: async () => (await api.get("/api/admin/events/incomplete")).data,
        refetchInterval: 10000,
    });

    const { data: completed, isLoading: loadingComp } = useQuery<EventPublication[]>({
        queryKey: ["events-completed"],
        queryFn: async () => (await api.get("/api/admin/events/completed")).data,
    });

    const retryMutation = useMutation({
        mutationFn: async () => api.post("/api/admin/events/resubmit?minutesAgo=0"),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["events-incomplete"] });
            queryClient.invalidateQueries({ queryKey: ["events-completed"] });
        },
    });

    const filteredIncomplete = incomplete?.filter(e => 
        e.eventType.toLowerCase().includes(search.toLowerCase()) || 
        e.eventPayload.toLowerCase().includes(search.toLowerCase())
    );

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-[1600px] mx-auto space-y-10">
                {/* Header */}
                <header className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
                    <div className="space-y-2">
                        <div className="flex items-center gap-3">
                            <div className="bg-amber-600/20 p-2 rounded-xl border border-amber-500/30 shadow-[0_0_15px_rgba(245,158,11,0.2)]">
                                <Zap className="w-6 h-6 text-amber-500" />
                            </div>
                            <h1 className="text-3xl font-black tracking-tighter italic uppercase text-white">
                                Event Publication Registry
                            </h1>
                        </div>
                        <p className="text-neutral-500 text-sm font-medium">Monitoring eventual consistency across system boundaries.</p>
                    </div>

                    <div className="flex items-center gap-3">
                        <div className="relative group">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-600 group-focus-within:text-amber-500 transition-colors" />
                            <input 
                                type="text"
                                placeholder="Filter events..."
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                className="pl-10 pr-4 py-2.5 bg-neutral-900/50 border border-neutral-800 rounded-xl focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500/50 w-64 transition-all"
                            />
                        </div>
                        <button
                            onClick={() => retryMutation.mutate()}
                            disabled={retryMutation.isPending}
                            className="flex items-center gap-2 px-6 py-2.5 bg-amber-600 hover:bg-amber-700 disabled:bg-neutral-800 rounded-xl font-bold transition-all shadow-lg hover:shadow-amber-500/20 active:scale-95"
                        >
                            <RefreshCcw className={`w-4 h-4 ${retryMutation.isPending ? 'animate-spin' : ''}`} />
                            Manual Retry
                        </button>
                    </div>
                </header>

                {/* Stats Grid */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="bg-neutral-900/50 border border-neutral-800 p-6 rounded-2xl flex items-center justify-between">
                        <div>
                            <p className="text-neutral-500 text-xs font-bold uppercase tracking-widest mb-1">Incomplete</p>
                            <h3 className="text-3xl font-black text-amber-500">{incomplete?.length || 0}</h3>
                        </div>
                        <AlertCircle className="w-10 h-10 text-amber-500/20" />
                    </div>
                    <div className="bg-neutral-900/50 border border-neutral-800 p-6 rounded-2xl flex items-center justify-between">
                        <div>
                            <p className="text-neutral-500 text-xs font-bold uppercase tracking-widest mb-1">Completed (7d)</p>
                            <h3 className="text-3xl font-black text-emerald-500">{completed?.length || 0}</h3>
                        </div>
                        <CheckCircle2 className="w-10 h-10 text-emerald-500/20" />
                    </div>
                    <div className="bg-neutral-900/50 border border-neutral-800 p-6 rounded-2xl flex items-center justify-between">
                        <div>
                            <p className="text-neutral-500 text-xs font-bold uppercase tracking-widest mb-1">Success Rate</p>
                            <h3 className="text-3xl font-black text-white">
                                {incomplete != null && completed != null 
                                    ? Math.round((completed.length / (incomplete.length + completed.length || 1)) * 100) 
                                    : 0}%
                            </h3>
                        </div>
                        <Activity className="w-10 h-10 text-blue-500/20" />
                    </div>
                </div>

                {/* Incomplete Events List */}
                <section className="space-y-6">
                    <div className="flex items-center gap-2">
                        <Database className="w-5 h-5 text-amber-500" />
                        <h2 className="text-xl font-black uppercase tracking-tight">Pending Publications</h2>
                    </div>

                    <div className="bg-neutral-900/30 border border-neutral-800 rounded-2xl overflow-hidden backdrop-blur-sm">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left">
                                <thead className="bg-neutral-900/50 border-b border-neutral-800">
                                    <tr>
                                        <th className="px-6 py-4 text-xs font-bold uppercase text-neutral-500 tracking-wider">Event / Listener</th>
                                        <th className="px-6 py-4 text-xs font-bold uppercase text-neutral-500 tracking-wider">Published</th>
                                        <th className="px-6 py-4 text-xs font-bold uppercase text-neutral-500 tracking-wider">Payload Snippet</th>
                                        <th className="px-6 py-4 text-xs font-bold uppercase text-neutral-500 tracking-wider">Status</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-neutral-800/50">
                                    {filteredIncomplete?.map((e) => (
                                        <tr key={e.id} className="hover:bg-amber-500/5 transition-colors group">
                                            <td className="px-6 py-4">
                                                <div className="flex flex-col">
                                                    <span className="font-bold text-white text-sm">{e.eventType}</span>
                                                    <span className="text-neutral-600 text-[10px] uppercase font-mono">{e.listenerId}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-2 text-neutral-400 text-xs">
                                                    <Clock className="w-3.5 h-3.5" />
                                                    {new Date(e.publicationDate).toLocaleTimeString()}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="text-neutral-500 text-xs italic line-clamp-1 max-w-xs font-mono">
                                                    {e.eventPayload}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className="px-3 py-1 bg-amber-500/10 text-amber-500 border border-amber-500/20 rounded-full text-[10px] font-black uppercase animate-pulse">
                                                    Incomplete
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                    {filteredIncomplete?.length === 0 && (
                                        <tr>
                                            <td colSpan={4} className="px-6 py-12 text-center text-neutral-600 italic">
                                                All systems operational. No incomplete publications found.
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
