'use client';

import { useEffect, useState } from 'react';
import axios from 'axios';

interface Migration {
    type: string;
    checksum: number;
    version: string;
    description: string;
    script: string;
    state: string;
    installedOn: string;
    executionTime: number;
}

interface FlywayResponse {
    contexts: {
        application: {
            flywayBeans: {
                flyway: {
                    migrations: Migration[];
                };
            };
        };
    };
}

export default function SystemStatusPage() {
    const [migrations, setMigrations] = useState<Migration[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const fetchStatus = async () => {
            try {
                // Assuming backend is at localhost:8080 via proxy or direct CORS
                const response = await axios.get('http://localhost:8080/actuator/flyway');
                // Structure might vary, strictly typing based on common actuator output
                const beans = response.data.contexts.application.flywayBeans;
                // Find the first bean that has migrations (usually just 'flyway')
                const flywayBean = Object.values(beans)[0] as { migrations: Migration[] };
                setMigrations(flywayBean.migrations);
            } catch (err) {
                setError('Failed to fetch migration status');
                console.error(err);
            } finally {
                setLoading(false);
            }
        };

        fetchStatus();
    }, []);

    if (loading) return <div className="p-8 text-white">Loading system status...</div>;
    if (error) return <div className="p-8 text-red-500">Error: {error}</div>;

    return (
        <div className="min-h-screen bg-slate-900 text-white p-8">
            <h1 className="text-3xl font-bold mb-8 text-blue-400">System Status (Flyway)</h1>

            <div className="bg-slate-800 rounded-lg p-6 shadow-lg border border-slate-700">
                <h2 className="text-xl font-semibold mb-4 text-emerald-400">Database Migrations</h2>
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="border-b border-slate-600 text-slate-400">
                                <th className="p-3">Version</th>
                                <th className="p-3">Description</th>
                                <th className="p-3">State</th>
                                <th className="p-3">Installed On</th>
                                <th className="p-3">Exec Time (ms)</th>
                            </tr>
                        </thead>
                        <tbody>
                            {migrations.map((m) => (
                                <tr key={m.version} className="border-b border-slate-700 hover:bg-slate-700/50">
                                    <td className="p-3 font-mono text-yellow-300">{m.version}</td>
                                    <td className="p-3">{m.description}</td>
                                    <td className={`p-3 font-bold ${m.state === 'SUCCESS' ? 'text-green-500' : 'text-red-500'}`}>
                                        {m.state}
                                    </td>
                                    <td className="p-3 text-sm text-slate-400">{m.installedOn}</td>
                                    <td className="p-3 text-right font-mono">{m.executionTime}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
