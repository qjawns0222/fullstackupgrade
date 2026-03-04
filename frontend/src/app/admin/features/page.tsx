"use client";

import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";

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
                <Badge variant="outline" className="px-3 py-1">Unleash (Simulated)</Badge>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {features.map((feature) => (
                    <Card key={feature.name} className="hover:shadow-md transition-shadow">
                        <CardHeader className="pb-2">
                            <div className="flex justify-between items-start">
                                <CardTitle className="text-xl font-semibold">{feature.name}</CardTitle>
                                <Badge variant={feature.enabled ? "default" : "secondary"}>
                                    {feature.enabled ? "Active" : "Disabled"}
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <p className="text-sm text-gray-500 mb-6">{feature.description}</p>
                            <div className="flex items-center space-x-2">
                                <Switch
                                    id={`toggle-${feature.name}`}
                                    checked={feature.enabled}
                                    onCheckedChange={() => toggleFeature(feature.name)}
                                />
                                <Label htmlFor={`toggle-${feature.name}`}>Toggle State</Label>
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>

            <Card className="bg-blue-50 border-blue-200">
                <CardContent className="pt-6">
                    <p className="text-sm text-blue-700">
                        <strong>Note:</strong> In this environment, the status above is simulated for demonstration.
                        In production, this UI connects to the Unleash API to control flags dynamically across all instances.
                    </p>
                </CardContent>
            </Card>
        </div>
    );
}
