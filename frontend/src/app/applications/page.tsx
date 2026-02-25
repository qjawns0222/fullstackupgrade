'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/lib/axios';
import { JobApplicationResponse } from '@/types/job-application';

export default function ApplicationsPage() {
    const queryClient = useQueryClient();

    const { data: applications, isLoading, isError } = useQuery<JobApplicationResponse[]>({
        queryKey: ['applications'],
        queryFn: async () => {
            const res = await api.get('/applications');
            return res.data;
        },
    });

    const mutation = useMutation({
        mutationFn: async ({ id, event }: { id: number; event: string }) => {
            const res = await api.post(`/applications/${id}/status?event=${event}`);
            return res.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['applications'] });
        },
    });

    const handleExport = async () => {
        try {
            const response = await api.get('/applications/export', {
                responseType: 'blob',
            });
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

    if (isLoading) return <div className="p-8 text-center text-white">Loading applications...</div>;
    if (isError) return <div className="p-8 text-center text-red-500">Failed to load applications.</div>;

    return (
        <div className="min-h-screen bg-gray-900 text-white p-8">
            <div className="max-w-6xl mx-auto">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">
                        Job Applications
                    </h1>
                    <button
                        onClick={handleExport}
                        className="px-6 py-2 bg-green-600 hover:bg-green-700 rounded-lg font-semibold transition-colors shadow-lg hover:shadow-green-500/30 flex items-center gap-2"
                    >
                        <span>Export to Excel</span>
                    </button>
                </div>

                <div className="bg-gray-800 rounded-xl shadow-xl overflow-hidden border border-gray-700">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-gray-700/50">
                                <th className="p-4 font-semibold text-gray-300">Company</th>
                                <th className="p-4 font-semibold text-gray-300">Position</th>
                                <th className="p-4 font-semibold text-gray-300">Status</th>
                                <th className="p-4 font-semibold text-gray-300">Applied Date</th>
                                <th className="p-4 font-semibold text-gray-300">Memo</th>
                                <th className="p-4 font-semibold text-gray-300">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-700">
                            {applications?.map((app) => (
                                <tr key={app.id} className="hover:bg-gray-700 transition-colors">
                                    <td className="p-4 font-medium text-white">{app.companyName}</td>
                                    <td className="p-4 text-gray-300">{app.position}</td>
                                    <td className="p-4">
                                        <span
                                            className={`px-3 py-1 rounded-full text-xs font-medium ${app.status === 'APPLIED' ? 'bg-blue-500/20 text-blue-400' :
                                                app.status === 'INTERVIEW' ? 'bg-yellow-500/20 text-yellow-400' :
                                                    app.status === 'REJECTED' ? 'bg-red-500/20 text-red-400' :
                                                        app.status === 'PASSED' ? 'bg-green-500/20 text-green-400' :
                                                            'bg-purple-500/20 text-purple-400'
                                                }`}
                                        >
                                            {app.status}
                                        </span>
                                    </td>
                                    <td className="p-4 text-gray-400">{app.appliedDate}</td>
                                    <td className="p-4 text-gray-500 italic truncate max-w-xs">{app.memo}</td>
                                    <td className="p-4">
                                        <div className="flex gap-2">
                                            {app.status === 'APPLIED' && (
                                                <>
                                                    <button onClick={() => mutation.mutate({ id: app.id, event: 'START_INTERVIEW' })} className="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-xs">Interview</button>
                                                    <button onClick={() => mutation.mutate({ id: app.id, event: 'REJECT' })} className="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-xs">Reject</button>
                                                </>
                                            )}
                                            {app.status === 'INTERVIEW' && (
                                                <>
                                                    <button onClick={() => mutation.mutate({ id: app.id, event: 'RECEIVE_OFFER' })} className="px-2 py-1 bg-purple-600 hover:bg-purple-700 rounded text-xs">Offer</button>
                                                    <button onClick={() => mutation.mutate({ id: app.id, event: 'REJECT' })} className="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-xs">Reject</button>
                                                </>
                                            )}
                                            {app.status === 'OFFER_RECEIVED' && (
                                                <>
                                                    <button onClick={() => mutation.mutate({ id: app.id, event: 'PASS' })} className="px-2 py-1 bg-green-600 hover:bg-green-700 rounded text-xs">Accept</button>
                                                    <button onClick={() => mutation.mutate({ id: app.id, event: 'REJECT' })} className="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-xs">Refuse</button>
                                                </>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {applications?.length === 0 && (
                        <div className="p-8 text-center text-gray-500">No applications found. Start applying!</div>
                    )}
                </div>
            </div>
        </div>
    );
}
