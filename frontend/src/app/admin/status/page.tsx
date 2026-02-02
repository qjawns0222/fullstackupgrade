'use client';

import { useState, useEffect } from 'react';
import axios from 'axios'; // direct axios
import api from '@/lib/axios'; // configured api

export default function SystemStatusPage() {
    const [health, setHealth] = useState<any>(null);
    const [circuitInternal, setCircuitInternal] = useState<any>(null);
    const [mailResult, setMailResult] = useState<string>('');
    const [loading, setLoading] = useState(false);

    const fetchHealth = async () => {
        try {
            // Fetch general health which includes circuit breakers if enabled
            const res = await axios.get('http://localhost:8000/actuator/health');
            setHealth(res.data);

            // Try to dig into details if exposed
            if (res.data.components?.circuitBreakers?.details?.mailService) {
                setCircuitInternal(res.data.components.circuitBreakers.details.mailService);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const sendTestMail = async () => {
        setLoading(true);
        setMailResult('Sending...');
        try {
            await api.post('/mail/test?email=test@example.com');
            setMailResult('Request Sent (Check Logs/Health for Fallback)');
        } catch (e: any) {
            console.error(e);
            setMailResult('Error: ' + (e.message || 'Unknown'));
        } finally {
            setLoading(false);
            fetchHealth(); // Refresh health
        }
    };

    useEffect(() => {
        fetchHealth();
        const interval = setInterval(fetchHealth, 3000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="p-8 max-w-4xl mx-auto">
            <h1 className="text-3xl font-bold mb-6">System Resilience Dashboard</h1>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-white p-6 rounded-lg shadow border border-gray-200">
                    <h2 className="text-xl font-semibold mb-4 text-gray-800">Mail Service Circuit Breaker</h2>
                    {health?.components?.circuitBreakers?.details?.mailService ? (
                        <div className="space-y-2">
                            <div className="flex justify-between">
                                <span className="text-gray-600">Status:</span>
                                <span className={`font-bold ${health.components.circuitBreakers.details.mailService.state === 'CLOSED' ? 'text-green-600' :
                                        health.components.circuitBreakers.details.mailService.state === 'OPEN' ? 'text-red-600' : 'text-yellow-600'
                                    }`}>
                                    {health.components.circuitBreakers.details.mailService.state}
                                </span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Failure Rate:</span>
                                <span>{health.components.circuitBreakers.details.mailService.metrics?.failureRate || 0}%</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Buffered Calls:</span>
                                <span>{health.components.circuitBreakers.details.mailService.metrics?.numberOfBufferedCalls || 0}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Failed Calls:</span>
                                <span>{health.components.circuitBreakers.details.mailService.metrics?.numberOfFailedCalls || 0}</span>
                            </div>
                        </div>
                    ) : (
                        <div className="text-gray-500 italic">
                            Circuit Breaker details not available. Check /actuator/health permission or layout.
                            <pre className="text-xs mt-2 overflow-auto max-h-40">{JSON.stringify(health, null, 2)}</pre>
                        </div>
                    )}
                </div>

                <div className="bg-white p-6 rounded-lg shadow border border-gray-200">
                    <h2 className="text-xl font-semibold mb-4 text-gray-800">Action</h2>
                    <p className="mb-4 text-gray-600">
                        Trigger a test email. If the SMTP server is unreachable, errors will be counted.
                        If the threshold is exceeded, the Circuit Breaker will OPEN.
                    </p>
                    <button
                        onClick={sendTestMail}
                        disabled={loading}
                        className="w-full px-4 py-3 bg-indigo-600 text-white font-medium rounded hover:bg-indigo-700 disabled:bg-gray-400 transition"
                    >
                        {loading ? 'Sending Request...' : 'Trigger Mail Service'}
                    </button>
                    {mailResult && (
                        <div className="mt-4 p-3 bg-gray-50 rounded text-sm text-center">
                            {mailResult}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
