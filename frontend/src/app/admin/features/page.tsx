"use client";

import { useState } from "react";

interface FeatureFlag {
    name: string;
    enabled: boolean;
    description: string;
}

export default function FeaturesAdminPage() {
    const [features, setFeatures] = useState<FeatureFlag[]>([
        { name: "ai-analysis", enabled: true, description: "Enable AI-powered OCR and Analysis" },
        { name: "batch-reporting", enabled: false, description: "Daily aggregate report generation" },
        { name: "new-dashboard", enabled: true, description: "Experimental dashboard view" },
    ]);

    const toggleFeature = (name: string) => {
        setFeatures(features.map(f =>
            f.name === name ? { ...f, enabled: !f.enabled } : f
        ));
    };

    return (
        <div className="p-6 space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-3xl font-bold">Feature Management</h1>
                <span className="px-3 py-1 border rounded-full text-sm text-gray-600">Unleash (Simulated)</span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {features.map((feature) => (
                    <div key={feature.name} className="bg-white border rounded-lg p-4 hover:shadow-md transition-shadow">
                        <div className="flex justify-between items-start mb-2">
                            <h2 className="text-lg font-semibold">{feature.name}</h2>
                            <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${feature.enabled ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"}`}>
                                {feature.enabled ? "Active" : "Disabled"}
                            </span>
                        </div>
                        <p className="text-sm text-gray-500 mb-4">{feature.description}</p>
                        <div className="flex items-center gap-3">
                            <button
                                role="switch"
                                aria-checked={feature.enabled}
                                onClick={() => toggleFeature(feature.name)}
                                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${feature.enabled ? "bg-blue-600" : "bg-gray-300"}`}
                            >
                                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${feature.enabled ? "translate-x-6" : "translate-x-1"}`} />
                            </button>
                            <label className="text-sm text-gray-600">Toggle State</label>
                        </div>
                    </div>
                ))}
            </div>

            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                <p className="text-sm text-blue-700">
                    <strong>Note:</strong> In this environment, the status above is simulated for demonstration.
                    In production, this UI connects to the Unleash API to control flags dynamically across all instances.
                </p>
            </div>
        </div>
    );
}
