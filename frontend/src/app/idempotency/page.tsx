'use client';

import { useState } from 'react';
import axios from '@/lib/axios';

export default function IdempotencyPage() {
  const [key, setKey] = useState(`key-${Date.now()}`);
  const [logs, setLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const testIdempotency = async () => {
    if (loading) return;
    setLoading(true);
    const currentKey = key;
    setLogs(prev => [...prev, `Starting batch request with Key: ${currentKey}`]);

    // Fire 3 requests in parallel to simulate duplicate requests
    const promises = [1, 2, 3].map(async (i) => {
      try {
        const response = await axios.post('/api/test/idempotency', 
          { data: `Request ${i}` },
          { headers: { 'Idempotency-Key': currentKey } }
        );
        return `Req ${i}: Success (${response.status})`;
      } catch (error: any) {
        const status = error.response?.status || 'Unknown';
        const msg = error.response?.data?.message || error.message;
        return `Req ${i}: Failed (${status} - ${msg})`;
      }
    });

    const results = await Promise.all(promises);
    setLogs(prev => [...prev, ...results]);
    setLoading(false);
  };

  return (
    <div className="p-8 max-w-2xl mx-auto">
      <h1 className="text-3xl font-bold mb-6 text-gray-800 dark:text-gray-100">Idempotency Test</h1>
      
      <div className="mb-6 bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
        <label className="block mb-2 font-semibold text-gray-700 dark:text-gray-200">Idempotency Key:</label>
        <div className="flex gap-2">
            <input 
              type="text" 
              value={key} 
              onChange={(e) => setKey(e.target.value)} 
              className="border border-gray-300 p-2 rounded w-full dark:bg-gray-700 dark:border-gray-600 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button 
              onClick={() => setKey(`key-${Date.now()}`)}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300 dark:bg-gray-600 dark:text-white transition-colors"
            >
              Refresh
            </button>
        </div>
        <p className="text-xs text-gray-500 mt-2">
            This key is sent in the 'Idempotency-Key' header. Backend invalidates duplicates processing within 5 seconds.
        </p>
      </div>

      <button 
        onClick={testIdempotency}
        disabled={loading}
        className={`w-full py-3 rounded-lg font-bold text-white transition-all ${
            loading 
            ? 'bg-blue-400 cursor-not-allowed' 
            : 'bg-blue-600 hover:bg-blue-700 shadow-lg hover:shadow-xl'
        }`}
      >
        {loading ? 'Sending...' : 'Send 3 Concurrent Requests'}
      </button>

      <div className="mt-8 bg-gray-900 text-green-400 p-4 rounded-lg font-mono text-sm h-64 overflow-auto shadow-inner border border-gray-700">
        <h3 className="font-bold mb-2 text-white border-b border-gray-700 pb-1">Logs:</h3>
        {logs.length === 0 && <span className="text-gray-500 italic">No logs yet...</span>}
        {logs.map((log, i) => (
          <div key={i} className="py-0.5">{`> ${log}`}</div>
        ))}
      </div>
    </div>
  );
}
