"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/axios";
import { JobApplicationResponse } from "@/types/job-application";
import { 
    Clock, 
    MessageSquare, 
    ArrowRight, 
    XCircle, 
    CheckCircle2, 
    MoreHorizontal,
    Briefcase
} from "lucide-react";

interface Props {
    applications: JobApplicationResponse[];
}

const STAGES = [
    { id: "APPLIED", label: "Applied", color: "bg-blue-500/10 text-blue-400 border-blue-500/20" },
    { id: "INTERVIEW", label: "Interview", color: "bg-yellow-500/10 text-yellow-400 border-yellow-500/20" },
    { id: "OFFER_RECEIVED", label: "Offer", color: "bg-purple-500/10 text-purple-400 border-purple-500/20" },
    { id: "PASSED", label: "Passed", color: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20" },
    { id: "REJECTED", label: "Rejected", color: "bg-rose-500/10 text-rose-400 border-rose-500/20" },
];

export default function KanbanBoard({ applications }: Props) {
    const queryClient = useQueryClient();

    const mutation = useMutation({
        mutationFn: async ({ id, event }: { id: number; event: string }) => {
            const res = await api.post(`/applications/${id}/status?event=${event}`);
            return res.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["applications"] });
        },
    });

    const getAvailableEvents = (status: string) => {
        switch (status) {
            case "APPLIED": return [
                { id: "START_INTERVIEW", label: "Interview", icon: MessageSquare, color: "bg-blue-600" },
                { id: "REJECT", label: "Reject", icon: XCircle, color: "bg-rose-600" }
            ];
            case "INTERVIEW": return [
                { id: "RECEIVE_OFFER", label: "Offer", icon: ArrowRight, color: "bg-purple-600" },
                { id: "REJECT", label: "Reject", icon: XCircle, color: "bg-rose-600" }
            ];
            case "OFFER_RECEIVED": return [
                { id: "PASS", label: "Accept", icon: CheckCircle2, color: "bg-emerald-600" },
                { id: "REJECT", label: "Decline", icon: XCircle, color: "bg-rose-600" }
            ];
            default: return [];
        }
    };

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6 h-full min-h-[70vh]">
            {STAGES.map((stage) => (
                <div key={stage.id} className="flex flex-col gap-4 bg-neutral-900/30 rounded-2xl p-4 border border-neutral-800/50 backdrop-blur-sm">
                    <div className="flex items-center justify-between px-2">
                        <div className={`flex items-center gap-2 px-3 py-1 rounded-full border text-xs font-bold uppercase tracking-wider ${stage.color}`}>
                            {stage.label}
                            <span className="opacity-50 ml-1">
                                {applications.filter((a) => a.status === stage.id).length}
                            </span>
                        </div>
                        <button className="text-neutral-600 hover:text-neutral-300">
                            <MoreHorizontal className="w-5 h-5" />
                        </button>
                    </div>

                    <div className="flex flex-col gap-3 h-full overflow-y-auto custom-scrollbar">
                        {applications
                            .filter((app) => app.status === stage.id)
                            .map((app) => (
                                <div 
                                    key={app.id} 
                                    className="bg-neutral-800/80 border border-neutral-700/50 p-4 rounded-xl shadow-lg hover:border-neutral-600 transition-all group flex flex-col gap-3"
                                >
                                    <div className="flex flex-col gap-1">
                                        <div className="flex items-center gap-2 text-neutral-400 text-[10px] uppercase font-bold tracking-tighter">
                                            <Briefcase className="w-3 h-3 text-blue-500" />
                                            {app.companyName}
                                        </div>
                                        <h4 className="text-white font-bold leading-tight line-clamp-2">{app.position}</h4>
                                    </div>

                                    {app.memo && <p className="text-neutral-500 text-xs italic line-clamp-2">"{app.memo}"</p>}

                                    <div className="flex items-center justify-between mt-2 pt-3 border-t border-neutral-700/50">
                                        <div className="flex items-center gap-1 text-[10px] text-neutral-600 font-mono italic font-bold">
                                            <Clock className="w-3 h-3" />
                                            {app.appliedDate}
                                        </div>
                                        
                                        <div className="flex gap-1">
                                            {getAvailableEvents(app.status).map((event) => (
                                                <button
                                                    key={event.id}
                                                    onClick={() => mutation.mutate({ id: app.id, event: event.id })}
                                                    className={`p-1.5 rounded-lg text-white opacity-40 group-hover:opacity-100 transition-all hover:scale-110 active:scale-95 ${event.color}`}
                                                    title={event.label}
                                                >
                                                    <event.icon className="w-3.5 h-3.5" />
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            ))}
                    </div>
                </div>
            ))}
        </div>
    );
}
