'use client';

import { useState } from 'react';
import { AlertCircle, CheckCircle, Play, RefreshCw, Trash2 } from 'lucide-react';

interface DeadlockMetrics {
  retryCount: number;
  successCount: number;
  failureCount: number;
}

interface SimulationResult {
  threads: number;
  results: string[];
  retryCount: number;
  successCount: number;
  failureCount: number;
}

export default function DeadlockMonitoringPage() {
  const [metrics, setMetrics] = useState<DeadlockMetrics | null>(null);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);
  const [threads, setThreads] = useState(5);
  const [loading, setLoading] = useState(false);

  const fetchMetrics = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/deadlock-test/metrics');
      const data = await response.json();
      setMetrics(data);
    } catch (error) {
      console.error('Failed to fetch metrics:', error);
    }
  };

  const simulateDeadlock = async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8080/api/deadlock-test/simulate?threads=${threads}`,
        { method: 'POST' }
      );
      const data = await response.json();
      setSimulationResult(data);
      await fetchMetrics();
    } catch (error) {
      console.error('Failed to simulate deadlock:', error);
    } finally {
      setLoading(false);
    }
  };

  const resetData = async () => {
    try {
      await fetch('http://localhost:8080/api/deadlock-test/reset', { method: 'POST' });
      setSimulationResult(null);
      await fetchMetrics();
    } catch (error) {
      console.error('Failed to reset data:', error);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 p-8">
      <div className="max-w-6xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-3xl font-bold text-white">Deadlock Recovery Monitoring</h1>
          <button
            onClick={fetchMetrics}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
          >
            <RefreshCw className="w-5 h-5" />
            Refresh Metrics
          </button>
        </div>

        {metrics && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <MetricCard
              title="Total Retries"
              value={metrics.retryCount}
              icon={<RefreshCw className="w-6 h-6 text-yellow-400" />}
              color="yellow"
            />
            <MetricCard
              title="Successful Recoveries"
              value={metrics.successCount}
              icon={<CheckCircle className="w-6 h-6 text-green-400" />}
              color="green"
            />
            <MetricCard
              title="Failed Recoveries"
              value={metrics.failureCount}
              icon={<AlertCircle className="w-6 h-6 text-red-400" />}
              color="red"
            />
          </div>
        )}

        <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
          <h2 className="text-xl font-semibold text-white mb-4">Simulate Deadlock</h2>
          <div className="flex items-end gap-4">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Number of Concurrent Threads
              </label>
              <input
                type="number"
                min="2"
                max="20"
                value={threads}
                onChange={(e) => setThreads(parseInt(e.target.value))}
                className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <button
              onClick={simulateDeadlock}
              disabled={loading}
              className="flex items-center gap-2 px-6 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 text-white rounded-lg transition-colors"
            >
              {loading ? (
                <>
                  <RefreshCw className="w-5 h-5 animate-spin" />
                  Running...
                </>
              ) : (
                <>
                  <Play className="w-5 h-5" />
                  Simulate
                </>
              )}
            </button>
            <button
              onClick={resetData}
              className="flex items-center gap-2 px-6 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors"
            >
              <Trash2 className="w-5 h-5" />
              Reset
            </button>
          </div>
        </div>

        {simulationResult && (
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <h2 className="text-xl font-semibold text-white mb-4">Simulation Results</h2>
            <div className="space-y-4">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="bg-gray-700 rounded-lg p-4">
                  <div className="text-sm text-gray-400">Threads</div>
                  <div className="text-2xl font-bold text-white">{simulationResult.threads}</div>
                </div>
                <div className="bg-gray-700 rounded-lg p-4">
                  <div className="text-sm text-gray-400">Retries</div>
                  <div className="text-2xl font-bold text-yellow-400">
                    {simulationResult.retryCount}
                  </div>
                </div>
                <div className="bg-gray-700 rounded-lg p-4">
                  <div className="text-sm text-gray-400">Success</div>
                  <div className="text-2xl font-bold text-green-400">
                    {simulationResult.successCount}
                  </div>
                </div>
                <div className="bg-gray-700 rounded-lg p-4">
                  <div className="text-sm text-gray-400">Failures</div>
                  <div className="text-2xl font-bold text-red-400">
                    {simulationResult.failureCount}
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <h3 className="text-lg font-medium text-white">Thread Results</h3>
                <div className="bg-gray-700 rounded-lg p-4 max-h-64 overflow-y-auto">
                  {simulationResult.results.map((result, index) => (
                    <div
                      key={index}
                      className={`flex items-center gap-2 py-2 ${
                        index !== simulationResult.results.length - 1
                          ? 'border-b border-gray-600'
                          : ''
                      }`}
                    >
                      {result === 'SUCCESS' ? (
                        <CheckCircle className="w-5 h-5 text-green-400 flex-shrink-0" />
                      ) : (
                        <AlertCircle className="w-5 h-5 text-red-400 flex-shrink-0" />
                      )}
                      <span
                        className={`text-sm ${
                          result === 'SUCCESS' ? 'text-green-400' : 'text-red-400'
                        }`}
                      >
                        Thread {index + 1}: {result}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
          <h2 className="text-xl font-semibold text-white mb-4">How It Works</h2>
          <div className="space-y-3 text-gray-300">
            <p>
              This monitoring dashboard tests the Spring Retry deadlock recovery mechanism by
              simulating concurrent database transactions that may cause deadlocks.
            </p>
            <ul className="list-disc list-inside space-y-2 ml-4">
              <li>
                The system automatically detects SQL deadlock exceptions (Error Code 1213, SQL State
                40001)
              </li>
              <li>
                Failed transactions are retried up to 5 times with exponential backoff (100ms to
                2000ms)
              </li>
              <li>Success rate and retry attempts are tracked via Micrometer metrics</li>
              <li>
                The @RetryOnDeadlock annotation enables automatic recovery without manual
                intervention
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}

function MetricCard({
  title,
  value,
  icon,
  color,
}: {
  title: string;
  value: number;
  icon: React.ReactNode;
  color: 'yellow' | 'green' | 'red';
}) {
  const colorClasses = {
    yellow: 'from-yellow-900/30 to-yellow-800/10 border-yellow-700/50',
    green: 'from-green-900/30 to-green-800/10 border-green-700/50',
    red: 'from-red-900/30 to-red-800/10 border-red-700/50',
  };

  return (
    <div
      className={`bg-gradient-to-br ${colorClasses[color]} rounded-xl p-6 border backdrop-blur-sm`}
    >
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-medium text-gray-300">{title}</h3>
        {icon}
      </div>
      <p className="text-3xl font-bold text-white">{value.toFixed(0)}</p>
    </div>
  );
}
