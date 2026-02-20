"use client";

import { useState, useEffect } from "react";
// axios is commonly used, but standard fetch is fine too. Using fetch for zero-dep simplicity or assume axios if project has it. 
// Given previous context mentioned axios interceptors, I'll use axios if possible, but fetch is safer if I'm not sure about the axios instance setup.
// Let's use fetch for standalone reliability in this file.

export default function AnalysisPage() {
    const [file, setFile] = useState<File | null>(null);
    const [uploadId, setUploadId] = useState<number | null>(null);
    const [status, setStatus] = useState<"IDLE" | "PENDING" | "ANALYZING" | "COMPLETED" | "FAILED">("IDLE");
    const [result, setResult] = useState<string | null>(null);
    const [progress, setProgress] = useState(0);

    // Fake progress animation effect for better UX
    useEffect(() => {
        let interval: NodeJS.Timeout;
        if (status === "ANALYZING" || status === "PENDING") {
            interval = setInterval(() => {
                setProgress((prev) => (prev < 90 ? prev + 5 : prev)); // Stop at 90% until done
            }, 500);
        } else if (status === "COMPLETED") {
            setProgress(100);
        }
        return () => clearInterval(interval);
    }, [status]);

    // Polling logic
    useEffect(() => {
        if (!uploadId || status === "COMPLETED" || status === "FAILED") return;

        const pollInterval = setInterval(async () => {
            try {
                const res = await fetch(`http://localhost:8000/api/analysis/${uploadId}`);
                if (res.ok) {
                    const data = await res.json();
                    // Map backend status to frontend status
                    setStatus(data.status);
                    if (data.status === "COMPLETED") {
                        setResult(data.result);
                        clearInterval(pollInterval);
                    } else if (data.status === "FAILED") {
                        setResult(data.result);
                        clearInterval(pollInterval);
                    }
                }
            } catch (error) {
                console.error("Polling error", error);
            }
        }, 1000); // Check every 1 second

        return () => clearInterval(pollInterval);
    }, [uploadId, status]);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
        }
    };

    const handleUpload = async () => {
        if (!file) return;

        const formData = new FormData();
        formData.append("file", file);

        try {
            setStatus("PENDING");
            setProgress(10);

            const res = await fetch("http://localhost:8000/api/analysis", {
                method: "POST",
                body: formData,
            });

            if (!res.ok) throw new Error("Upload failed");

            const data = await res.json();
            setUploadId(data.id);
            // Status will be updated by polling
        } catch (error) {
            console.error(error);
            setStatus("FAILED");
            setResult("Upload failed. Please try again.");
        }
    };

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center p-6">
            <div className="max-w-md w-full bg-white rounded-xl shadow-lg p-8">
                <h1 className="text-2xl font-bold text-gray-800 mb-6 text-center">AI 분석 요청 (S3 Object Storage 연동)</h1>

                {/* Upload Section */}
                {status === "IDLE" && (
                    <div className="space-y-4">
                        <div className="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center hover:border-blue-500 transition-colors">
                            <input
                                type="file"
                                onChange={handleFileChange}
                                className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
                            />
                        </div>

                        <button
                            onClick={handleUpload}
                            disabled={!file}
                            className={`w-full py-3 rounded-lg font-semibold text-white transition-all
                ${file ? "bg-blue-600 hover:bg-blue-700 shadow-md" : "bg-gray-300 cursor-not-allowed"}
              `}
                        >
                            분석 시작
                        </button>
                    </div>
                )}

                {/* Progress Section */}
                {(status === "PENDING" || status === "ANALYZING") && (
                    <div className="space-y-6 animate-fade-in">
                        <div className="flex flex-col items-center">
                            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                            <h2 className="text-lg font-semibold text-gray-700">AI가 분석 중입니다...</h2>
                            <p className="text-sm text-gray-500 mt-1">잠시만 기다려주세요</p>
                        </div>

                        {/* Progress Bar */}
                        <div className="w-full bg-gray-200 rounded-full h-2.5 overflow-hidden">
                            <div
                                className="bg-blue-600 h-2.5 rounded-full transition-all duration-500 ease-out"
                                style={{ width: `${progress}%` }}
                            ></div>
                        </div>
                    </div>
                )}

                {/* Result Section */}
                {status === "COMPLETED" && (
                    <div className="space-y-4 text-center animate-fade-in">
                        <div className="flex justify-center mb-4">
                            <div className="bg-green-100 p-3 rounded-full">
                                <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7"></path>
                                </svg>
                            </div>
                        </div>
                        <h2 className="text-xl font-bold text-gray-800">분석 완료!</h2>
                        <div className="bg-gray-50 p-4 rounded-lg text-left border border-gray-100">
                            <p className="text-gray-700 whitespace-pre-wrap">{result}</p>
                        </div>
                        <button
                            onClick={() => {
                                setStatus("IDLE");
                                setFile(null);
                                setResult(null);
                                setUploadId(null);
                                setProgress(0);
                            }}
                            className="mt-4 text-blue-600 hover:text-blue-800 font-semibold"
                        >
                            다른 파일 분석하기
                        </button>
                    </div>
                )}

                {status === "FAILED" && (
                    <div className="space-y-4 text-center animate-fade-in">
                        <div className="flex justify-center mb-4">
                            <div className="bg-red-100 p-3 rounded-full">
                                <svg className="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                                </svg>
                            </div>
                        </div>
                        <h2 className="text-xl font-bold text-gray-800">분석 실패</h2>
                        <p className="text-gray-600">{result}</p>
                        <button
                            onClick={() => {
                                setStatus("IDLE");
                                setFile(null);
                                setResult(null);
                                setUploadId(null);
                                setProgress(0);
                            }}
                            className="mt-4 text-blue-600 hover:text-blue-800 font-semibold"
                        >
                            다시 시도하기
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
