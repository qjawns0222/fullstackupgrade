'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import api from '@/lib/axios';
import { JobApplicationResponse } from '@/types/job-application';
import { Briefcase, ArrowRight } from 'lucide-react';
import KanbanBoard from '@/components/applications/KanbanBoard';

export default function ApplicationsPage() {
    const queryClient = useQueryClient();

    const { data: applications, isLoading, isError } = useQuery<JobApplicationResponse[]>({
        queryKey: ['applications'],
        queryFn: async () => {
            const res = await api.get('/applications');
            return res.data;
        },
    });

    const handleExport = async () => {
        try {
            const response = await api.get('/applications/export', { responseType: 'blob' });
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'job_applications.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch (error) {
            console.error('Export failed', error);
            alert('Failed to export job applications.');
        }
    };

    if (isLoading) return (
        <div className="min-h-screen bg-neutral-950 flex items-center justify-center">
            <div className="flex flex-col items-center gap-4">
                <div className="w-12 h-12 border-4 border-blue-500/20 border-t-blue-500 rounded-full animate-spin" />
                <p className="text-neutral-400 font-bold animate-pulse">Initializing Kanban...</p>
            </div>
        </div>
    );

    if (isError) return <div className="p-8 text-center text-red-500">Failed to load applications.</div>;

    return (
        <div className="min-h-screen bg-neutral-950 text-neutral-100 p-8 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-[1600px] mx-auto space-y-10">
                {/* Dashboard-style Header */}
                <header className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
                    <div className="space-y-2">
                        <div className="flex items-center gap-3">
                            <div className="bg-blue-600/20 p-2 rounded-xl border border-blue-500/30 shadow-[0_0_15px_rgba(59,130,246,0.2)]">
                                <Briefcase className="w-6 h-6 text-blue-500" />
                            </div>
                            <h1 className="text-3xl font-black tracking-tighter italic uppercase text-white shadow-sm">
                                Career Pipeline
                            </h1>
                        </div>
                        <p className="text-neutral-500 text-sm font-medium">Manage your job application lifecycle with persistent state management.</p>
                    </div>

                    <div className="flex items-center gap-4">
                        <div className="hidden md:flex flex-col items-end px-4 py-2 border-r border-neutral-800">
                            <span className="text-[10px] text-neutral-500 font-bold uppercase">Total Opportunities</span>
                            <span className="text-xl font-black text-white">{applications?.length || 0}</span>
                        </div>
                        <button
                            onClick={handleExport}
                            className="group relative px-6 py-2.5 bg-neutral-900 border border-neutral-800 hover:border-emerald-500/50 rounded-xl font-bold transition-all overflow-hidden"
                        >
                            <div className="absolute inset-0 bg-emerald-500/10 opacity-0 group-hover:opacity-100 transition-opacity" />
                            <span className="relative flex items-center gap-2 text-neutral-300 group-hover:text-emerald-400">
                                <ArrowRight className="w-4 h-4" />
                                Export Excel
                            </span>
                        </button>
                    </div>
                </header>

                <main className="relative">
                    <div className="absolute -inset-4 bg-gradient-to-b from-blue-500/5 to-transparent opacity-50 blur-3xl pointer-events-none" />
                    <KanbanBoard applications={applications || []} />
                </main>

                {applications?.length === 0 && (
                    <div className="py-20 text-center space-y-4 bg-neutral-900/50 border border-dashed border-neutral-800 rounded-3xl">
                        <Briefcase className="w-12 h-12 text-neutral-700 mx-auto" />
                        <p className="text-neutral-500 font-bold tracking-tight">Your pipeline is empty. Start your journey today.</p>
                    </div>
                )}
            </div>
        </div>
    );
}
