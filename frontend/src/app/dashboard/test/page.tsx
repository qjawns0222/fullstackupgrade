"use client"

import { useState, useCallback } from 'react';
import api from '@/lib/axios';

interface DashboardData {
    userId: string;
    data: string;
    timestamp: string;
}

export default function DashboardTestPage() {
    const [data, setData] = useState<DashboardData | null>(null);
    const [loading, setLoading] = useState(false);
    const [userId, setUserId] = useState("user123");

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const response = await api.get<DashboardData>(`/dashboard/${userId}`);
            setData(response.data);
        } catch (error) {
            console.error("Failed to fetch data", error);
            alert("데이터 조회 실패");
        } finally {
            setLoading(false);
        }
    }, [userId]);

    return (
        <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4">
            <div className="max-w-md w-full bg-white rounded-xl shadow-lg overflow-hidden md:max-w-2xl">
                <div className="p-8">
                    <div className="uppercase tracking-wide text-sm text-indigo-500 font-semibold mb-4">Redis Caching Test</div>
                    <h1 className="block mt-1 text-lg leading-tight font-medium text-black">Dashboard Data Verification</h1>
                    <p className="mt-2 text-gray-500">
                        데이터를 조회하여 타임스탬프를 확인하세요. <br />
                        캐싱된 경우 타임스탬프가 변경되지 않습니다. (TTL: 60초)
                    </p>

                    <div className="mt-6">
                        <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="userId">
                            User ID
                        </label>
                        <input
                            className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline focus:border-indigo-500 transition duration-300"
                            id="userId"
                            type="text"
                            value={userId}
                            onChange={(e) => setUserId(e.target.value)}
                        />
                    </div>

                    <div className="mt-6 flex justify-center">
                        <button
                            onClick={fetchData}
                            disabled={loading}
                            className={`w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 px-4 rounded-lg shadow-md transition duration-300 transform hover:scale-105 flex justify-center items-center ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                        >
                            {loading ? (
                                <svg className="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                </svg>
                            ) : (
                                "Load Data"
                            )}
                        </button>
                    </div>

                    {data && (
                        <div className="mt-8 bg-gray-100 rounded-lg p-6 border border-gray-200 animate-fade-in-up">
                            <h2 className="text-xl font-bold text-gray-800 mb-4">Result</h2>
                            <div className="space-y-3">
                                <div className="flex justify-between border-b pb-2">
                                    <span className="font-semibold text-gray-600">User ID:</span>
                                    <span className="text-gray-800">{data.userId}</span>
                                </div>
                                <div className="flex justify-between border-b pb-2">
                                    <span className="font-semibold text-gray-600">Data:</span>
                                    <span className="text-gray-800">{data.data}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="font-semibold text-gray-600">Timestamp:</span>
                                    <span className="text-indigo-600 font-mono font-bold">{data.timestamp}</span>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
