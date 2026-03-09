"use client";

import { useState, useEffect, useRef } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

export default function AnalysisPage() {
    const [file, setFile] = useState<File | null>(null);
    const [uploadId, setUploadId] = useState<number | null>(null);
    const [status, setStatus] = useState<"IDLE" | "PENDING" | "ANALYZING" | "COMPLETED" | "FAILED">("IDLE");
    const [logMessage, setLogMessage] = useState<string>("잠시만 기다려주세요");
    const [result, setResult] = useState<string | null>(null);
    const [progress, setProgress] = useState(0);
    const stompClientRef = useRef<Client | null>(null);

    // Progress animation
    useEffect(() => {
        let interval: NodeJS.Timeout;
        if (status === "ANALYZING" || status === "PENDING") {
            interval = setInterval(() => {
                setProgress((prev) => (prev < 95 ? prev + 2 : prev));
            }, 800);
        } else if (status === "COMPLETED") {
            setProgress(100);
        }
        return () => clearInterval(interval);
    }, [status]);

    // WebSocket Connection
    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS("http://localhost:8000/ws"),
            debug: (msg) => console.log(msg),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        client.onConnect = () => {
            console.log("Connected to WebSocket");
            // Subscribe to user-specific analysis topic
            // Spring's convertAndSendToUser sends to /user/{username}/topic/analysis
            // Client subscribes to /user/topic/analysis
            client.subscribe("/user/topic/analysis", (message) => {
                const update = JSON.parse(message.body);
                console.log("Received update:", update);
                handleStatusUpdate(update);
            });
        };

        client.onStompError = (frame) => {
            console.error("STOMP error", frame.headers["message"]);
        };

        client.activate();
        stompClientRef.current = client;

        return () => {
            if (stompClientRef.current) {
                stompClientRef.current.deactivate();
            }
        };
    }, []);

    const handleStatusUpdate = (update: any) => {
        const { status: newStatus, message, requestId } = update;

        // Ensure this update is for the current upload
        // Note: In a real app, you might want more robust ID checking

        setLogMessage(message);

        switch (newStatus) {
            case "STARTED":
            case "PROCESSING":
                setStatus("ANALYZING");
                break;
            case "COMPLETED":
                setStatus("COMPLETED");
                fetchResult(requestId);
                break;
            case "FAILED":
                setStatus("FAILED");
                setResult(message);
                break;
        }
    };

    const fetchResult = async (id: number) => {
        try {
            const res = await fetch(`http://localhost:8000/api/analysis/${id}`);
            if (res.ok) {
                const data = await res.json();
                setResult(data.result);
            }
        } catch (error) {
            console.error("Failed to fetch final result", error);
        }
    };

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
            setLogMessage("파일을 업로드하는 중...");
            setProgress(10);

            const res = await fetch("http://localhost:8000/api/analysis", {
                method: "POST",
                body: formData,
            });

            if (!res.ok) {
                if (res.status === 429) {
                    throw { status: 429 };
                }
                throw new Error("Upload failed");
            }

            const data = await res.json();
            setUploadId(data.id);
            // Real-time updates will take over from here
        } catch (error: any) {
            console.error(error);
            setStatus("FAILED");
            if (error.status === 429) {
                setResult("너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요. (Rate Limit)");
            } else {
                setResult("업로드에 실패했습니다. 파일 형식을 확인하거나 나중에 다시 시도해주세요.");
            }
        }
    };

    return (
        <div className="min-h-screen bg-linear-to-br from-slate-50 to-slate-100 flex items-center justify-center p-6 font-[family-name:var(--font-geist-sans)]">
            <div className="max-w-md w-full bg-white/80 backdrop-blur-md rounded-2xl shadow-2xl border border-white/20 p-8 transition-all hover:shadow-blue-500/10">
                <div className="flex flex-col items-center mb-8">
                    <div className="bg-blue-600 p-3 rounded-xl shadow-lg shadow-blue-200 mb-4">
                        <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.364-6.364l-.707-.707M8.414 8.414l-.707-.707M12 21v-1m-4.636-1.636l.707-.707M17.586 17.586l.707-.707M12 11a1 1 0 100-2 1 1 0 000 2z" />
                        </svg>
                    </div>
                    <h1 className="text-2xl font-bold bg-clip-text text-transparent bg-linear-to-r from-slate-900 to-slate-700">AI Resume 분석</h1>
                    <p className="text-slate-500 text-sm mt-1">실시간 WebSocket 통신 활성화됨</p>
                </div>

                {status === "IDLE" && (
                    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
                        <div className="group relative border-2 border-dashed border-slate-200 rounded-2xl p-10 text-center hover:border-blue-400 hover:bg-blue-50/30 transition-all cursor-pointer">
                            <input
                                type="file"
                                onChange={handleFileChange}
                                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
                                accept=".pdf,.doc,.docx"
                            />
                            <div className="space-y-2 pointer-events-none">
                                <div className="mx-auto w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center group-hover:bg-blue-100 transition-colors">
                                    <svg className="w-6 h-6 text-slate-400 group-hover:text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                                    </svg>
                                </div>
                                <p className="text-sm font-medium text-slate-700">{file ? file.name : "이력서 파일을 드래그하거나 클릭하세요"}</p>
                                <p className="text-xs text-slate-400 text-pretty">PDF, DOCX 형식 지원 (최대 10MB)</p>
                            </div>
                        </div>

                        <button
                            onClick={handleUpload}
                            disabled={!file}
                            className={`group relative w-full py-4 rounded-xl font-bold text-white overflow-hidden transition-all active:scale-95
                ${file ? "bg-blue-600 hover:bg-blue-700 shadow-[0_8px_30px_rgb(37,99,235,0.3)]" : "bg-slate-200 cursor-not-allowed"}
              `}
                        >
                            <span className="relative z-10 flex items-center justify-center gap-2">
                                분석 시작하기
                                <svg className="w-5 h-5 group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                                </svg>
                            </span>
                        </button>
                    </div>
                )}

                {(status === "PENDING" || status === "ANALYZING") && (
                    <div className="space-y-8 animate-in zoom-in-95 duration-500">
                        <div className="flex flex-col items-center text-center">
                            <div className="relative mb-6">
                                <div className="absolute inset-0 bg-blue-500/20 blur-xl rounded-full animate-pulse"></div>
                                <div className="relative animate-spin rounded-full h-16 w-16 border-4 border-slate-100 border-t-blue-600 shadow-inner"></div>
                            </div>
                            <h2 className="text-xl font-bold text-slate-800">{logMessage}</h2>
                            <p className="text-slate-400 text-sm mt-2">서버에서 실시간 데이터를 수신 중입니다</p>
                        </div>

                        <div className="space-y-2">
                            <div className="flex justify-between text-xs font-bold text-slate-500 mb-1">
                                <span>분석 진행률</span>
                                <span>{progress}%</span>
                            </div>
                            <div className="w-full bg-slate-100 rounded-full h-3 overflow-hidden shadow-inner">
                                <div
                                    className="bg-linear-to-r from-blue-600 to-indigo-500 h-full rounded-full transition-all duration-700 ease-out shadow-lg"
                                    style={{ width: `${progress}%` }}
                                ></div>
                            </div>
                        </div>
                    </div>
                )}

                {status === "COMPLETED" && (
                    <div className="space-y-6 animate-in fade-in duration-700">
                        <div className="flex flex-col items-center">
                            <div className="bg-emerald-500/10 p-4 rounded-full mb-4 animate-bounce">
                                <svg className="w-10 h-10 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7"></path>
                                </svg>
                            </div>
                            <h2 className="text-2xl font-black text-slate-900">분석이 완벽하게 완료되었습니다!</h2>
                        </div>

                        <div className="bg-slate-50/50 rounded-2xl p-6 border border-slate-100 max-h-60 overflow-y-auto custom-scrollbar shadow-inner">
                            <p className="text-slate-700 text-sm leading-relaxed whitespace-pre-wrap">{result}</p>
                        </div>

                        <div className="grid grid-cols-1 gap-4">
                            <button
                                onClick={async () => {
                                    const res = await fetch(`http://localhost:8000/api/analysis/${uploadId}/export`);
                                    if (res.ok) {
                                        const blob = await res.blob();
                                        const url = window.URL.createObjectURL(blob);
                                        const a = document.createElement("a");
                                        a.href = url;
                                        a.download = `analysis_report_${uploadId}.pdf`;
                                        a.click();
                                    }
                                }}
                                className="w-full py-4 bg-slate-900 hover:bg-black text-white font-bold rounded-xl shadow-xl transition-all active:scale-95 flex items-center justify-center gap-2"
                            >
                                <svg className="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                </svg>
                                PDF 리포트 다운로드
                            </button>
                            <button
                                onClick={() => {
                                    setStatus("IDLE");
                                    setFile(null);
                                    setResult(null);
                                    setUploadId(null);
                                    setProgress(0);
                                }}
                                className="py-2 text-slate-500 hover:text-blue-600 font-bold text-sm transition-colors"
                            >
                                다른 파일 분석하기
                            </button>
                        </div>
                    </div>
                )}

                {status === "FAILED" && (
                    <div className="space-y-6 text-center animate-in slide-in-from-top-4 duration-500">
                        <div className="bg-rose-500/10 p-5 rounded-full inline-block mx-auto">
                            <svg className="w-12 h-12 text-rose-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                        </div>
                        <h2 className="text-2xl font-black text-slate-900">분석 실패</h2>
                        <div className="bg-rose-50 p-4 rounded-xl border border-rose-100 text-sm text-rose-700">
                            {result}
                        </div>
                        <button
                            onClick={() => {
                                setStatus("IDLE");
                                setFile(null);
                                setResult(null);
                                setUploadId(null);
                                setProgress(0);
                            }}
                            className="bg-slate-900 text-white w-full py-4 rounded-xl font-bold hover:bg-black transition-all"
                        >
                            다시 시도하기
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
