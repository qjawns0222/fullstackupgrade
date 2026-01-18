"use client";

import { useEffect, useState, useMemo } from 'react';
import api from '@/lib/axios';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
    Cell
} from 'recharts';
import { Loader2, RefreshCw } from 'lucide-react';

interface TrendStats {
    id: number;
    techStack: string;
    count: number;
    recordedAt: string;
}

export default function TrendsPage() {
    const [data, setData] = useState<TrendStats[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchData = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await api.get('/trends');
            setData(response.data);
        } catch (err) {
            setError('데이터를 불러오는데 실패했습니다.');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    // Process data to get the latest snapshot
    const latestData = useMemo(() => {
        if (data.length === 0) return [];

        // Find the latest recordedAt timestamp
        const times = data.map(d => new Date(d.recordedAt).getTime());
        const maxTime = Math.max(...times);

        // Filter items that match the latest time
        // Allow a small window (e.g., 1 minute) in case batch writes are slightly offset, 
        // but ideally they are close. Or just strict match.
        // Since we save in one transaction/step, the timestamps might slightly differ if we use LocalDateTime.now() in entity constructor 
        // vs passing it. The entity has `var recordedAt: LocalDateTime = LocalDateTime.now()`.
        // So usually they will differ by milliseconds.
        // Better strategy: Group by "Hour" or finding the "Batch Run". 
        // For now, let's sort by recordedAt desc and take the top N distinct tech stacks?
        // No, that mixes runs.

        // Let's grouping by `recordedAt` roughly (to the minute).
        // Or simpler: The backend controller returns ALL. 
        // Let's assume the user just ran it once or we show the validation of the "current" state.

        // Attempt to cluster by time (within 5 seconds)
        const sortedData = [...data].sort((a, b) => new Date(b.recordedAt).getTime() - new Date(a.recordedAt).getTime());

        if (sortedData.length === 0) return [];

        const latestTimeString = sortedData[0].recordedAt;
        const latestTime = new Date(latestTimeString).getTime();

        // Filter data within 10 seconds of the latest record
        return sortedData
            .filter(d => Math.abs(new Date(d.recordedAt).getTime() - latestTime) < 10000)
            .sort((a, b) => b.count - a.count);

    }, [data]);

    const latestTimeDisplay = latestData.length > 0
        ? new Date(latestData[0].recordedAt).toLocaleString()
        : '-';

    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8', '#82ca9d', '#ffc658', '#8dd1e1', '#a4de6c', '#d0ed57'];

    return (
        <div className="p-8 max-w-7xl mx-auto space-y-8">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
                        기술 스택 트렌드
                    </h1>
                    <p className="text-gray-500 mt-2">이력서 분석을 통한 실시간 인기 기술 스택 현황</p>
                </div>
                <button
                    onClick={fetchData}
                    disabled={loading}
                    className="flex items-center gap-2 px-4 py-2 bg-white border border-gray-200 rounded-lg shadow-sm hover:bg-gray-50 transition-colors disabled:opacity-50"
                >
                    {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                    새로고침
                </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Chart Section */}
                <div className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                    <div className="mb-6 flex justify-between items-center">
                        <h2 className="text-xl font-semibold text-gray-800">상위 기술 스택</h2>
                        <span className="text-sm text-gray-400">기준: {latestTimeDisplay}</span>
                    </div>

                    {loading ? (
                        <div className="h-[400px] flex items-center justify-center">
                            <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
                        </div>
                    ) : latestData.length > 0 ? (
                        <div className="h-[400px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart
                                    data={latestData}
                                    layout="vertical"
                                    margin={{ top: 5, right: 30, left: 40, bottom: 5 }}
                                >
                                    <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                    <XAxis type="number" />
                                    <YAxis type="category" dataKey="techStack" width={80} />
                                    <Tooltip
                                        contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                                        cursor={{ fill: '#f3f4f6' }}
                                    />
                                    <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                                        {latestData.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    ) : (
                        <div className="h-[400px] flex items-center justify-center text-gray-400">
                            데이터가 없습니다.
                        </div>
                    )}
                </div>

                {/* Stats List */}
                <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                    <h2 className="text-xl font-semibold text-gray-800 mb-6">상세 현황</h2>
                    <div className="space-y-4">
                        {loading ? (
                            Array.from({ length: 5 }).map((_, i) => (
                                <div key={i} className="h-12 bg-gray-100 rounded-lg animate-pulse" />
                            ))
                        ) : latestData.length > 0 ? (
                            latestData.map((item, index) => (
                                <div key={item.id} className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg transition-colors group">
                                    <div className="flex items-center gap-3">
                                        <span className={`flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold ${index < 3 ? 'bg-blue-100 text-blue-600' : 'bg-gray-100 text-gray-500'}`}>
                                            {index + 1}
                                        </span>
                                        <span className="font-medium text-gray-700">{item.techStack}</span>
                                    </div>
                                    <span className="font-bold text-gray-900 group-hover:text-blue-600 transition-colors">
                                        {item.count} <span className="text-xs font-normal text-gray-400 ml-1">건</span>
                                    </span>
                                </div>
                            ))
                        ) : (
                            <div className="text-center text-gray-400 py-10">
                                데이터가 없습니다.
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
