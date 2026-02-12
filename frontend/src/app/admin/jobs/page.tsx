import React from 'react';

export default function JobsDashboardPage() {
    return (
        <div className="container mx-auto px-4 py-8 h-[calc(100vh-64px)] flex flex-col">
            <h1 className="text-2xl font-bold mb-4 text-gray-800 dark:text-gray-100">Background Job Dashboard (JobRunr)</h1>
            <div className="flex-1 border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden shadow-lg bg-white dark:bg-gray-800">
                <iframe
                    src="http://localhost:8000/dashboard"
                    className="w-full h-full border-0"
                    title="JobRunr Dashboard"
                />
            </div>
        </div>
    );
}
