"use client";

import { useState, useRef } from "react";
import api from "@/lib/axios";

type SagaStatus =
    | "STARTED"
    | "S3_UPLOADED"
    | "DB_SAVED"
    | "ES_INDEXED"
    | "COMPLETED"
    | "COMPENSATING"
    | "COMPENSATED"
    | "FAILED";

interface SagaResult {
    sagaId: string;
    success: boolean;
    resumeId: number | null;
    fileKey: string | null;
    errorMessage: string | null;
}

interface SagaState {
    sagaId: string;
    userId: number;
    originalFileName: string;
    status: SagaStatus;
    s3FileKey: string | null;
    resumeId: number | null;
    errorMessage: string | null;
    createdAt: number;
}

const statusConfig: Record<SagaStatus, { label: string; color: string; bg: string }> = {
    STARTED:      { label: "시작됨",        color: "text-blue-600",   bg: "bg-blue-50" },
    S3_UPLOADED:  { label: "S3 업로드 완료", color: "text-indigo-600", bg: "bg-indigo-50" },
    DB_SAVED:     { label: "DB 저장 완료",   color: "text-violet-600", bg: "bg-violet-50" },
    ES_INDEXED:   { label: "ES 인덱싱 완료", color: "text-purple-600", bg: "bg-purple-50" },
    COMPLETED:    { label: "완료",           color: "text-emerald-600", bg: "bg-emerald-50" },
    COMPENSATING: { label: "보상 중",         color: "text-orange-600", bg: "bg-orange-50" },
    COMPENSATED:  { label: "보상 완료",       color: "text-yellow-700", bg: "bg-yellow-50" },
    FAILED:       { label: "실패",            color: "text-red-600",    bg: "bg-red-50" },
};

const steps = [
    { key: "S3_UPLOADED",  label: "S3 업로드" },
    { key: "DB_SAVED",     label: "DB 저장" },
    { key: "ES_INDEXED",   label: "ES 인덱싱" },
    { key: "COMPLETED",    label: "완료" },
];

const stepOrder: SagaStatus[] = ["STARTED", "S3_UPLOADED", "DB_SAVED", "ES_INDEXED", "COMPLETED"];

function getStepDone(current: SagaStatus, stepKey: string): "done" | "active" | "error" | "pending" {
    if (current === "FAILED" || current === "COMPENSATING" || current === "COMPENSATED") {
        const currentIdx = stepOrder.indexOf(current as SagaStatus);
        const stepIdx = stepOrder.indexOf(stepKey as SagaStatus);
        if (stepIdx < currentIdx) return "done";
        return "error";
    }
    const currentIdx = stepOrder.indexOf(current);
    const stepIdx = stepOrder.indexOf(stepKey as SagaStatus);
    if (stepIdx < currentIdx) return "done";
    if (stepIdx === currentIdx) return "active";
    return "pending";
}

export default function SagaMonitorPage() {
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [sagaResult, setSagaResult] = useState<SagaResult | null>(null);
    const [sagaState, setSagaState] = useState<SagaState | null>(null);
    const [queryId, setQueryId] = useState("");
    const [activeKeys, setActiveKeys] = useState<string[]>([]);
    const fileRef = useRef<HTMLInputElement>(null);

    const handleUpload = async () => {
        if (!file) return;
        setUploading(true);
        setSagaResult(null);
        setSagaState(null);

        const formData = new FormData();
        formData.append("file", file);

        try {
            const res = await api.post<SagaResult>("/resumes/upload", formData, {
                headers: { "Content-Type": "multipart/form-data" },
            });
            setSagaResult(res.data);
            if (res.data.sagaId) {
                await fetchSagaState(res.data.sagaId);
            }
        } catch (e: any) {
            const errData = e.response?.data;
            setSagaResult({
                sagaId: errData?.sagaId || "-",
                success: false,
                resumeId: null,
                fileKey: null,
                errorMessage: errData?.errorMessage || "업로드 실패",
            });
        } finally {
            setUploading(false);
        }
    };

    const fetchSagaState = async (id: string) => {
        try {
            const res = await api.get<SagaState>(`/saga/resume/${id}`);
            setSagaState(res.data);
        } catch {
            setSagaState(null);
        }
    };

    const fetchActiveKeys = async () => {
        try {
            const res = await api.get<string[]>("/saga/resume/active");
            setActiveKeys(res.data);
        } catch {
            setActiveKeys([]);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 p-6">
            <div className="max-w-3xl mx-auto space-y-6">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900">Saga 분산 트랜잭션 모니터</h1>
                    <p className="text-slate-500 text-sm mt-1">
                        Resume 업로드 파이프라인(S3 → DB → ES)의 원자성을 보장합니다. 실패 시 자동 보상 트랜잭션이 실행됩니다.
                    </p>
                </div>

                {/* 업로드 테스트 */}
                <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
                    <h2 className="font-semibold text-slate-800">이력서 업로드 (Saga 실행)</h2>
                    <div className="flex gap-3 items-center">
                        <input
                            ref={fileRef}
                            type="file"
                            className="hidden"
                            onChange={(e) => setFile(e.target.files?.[0] || null)}
                            accept=".pdf,.doc,.docx"
                        />
                        <button
                            onClick={() => fileRef.current?.click()}
                            className="px-4 py-2 border border-slate-300 rounded-lg text-sm text-slate-700 hover:bg-slate-50 transition"
                        >
                            {file ? file.name : "파일 선택"}
                        </button>
                        <button
                            onClick={handleUpload}
                            disabled={!file || uploading}
                            className="px-5 py-2 bg-blue-600 text-white rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                        >
                            {uploading ? "업로드 중..." : "Saga 실행"}
                        </button>
                    </div>

                    {sagaResult && (
                        <div className={`rounded-lg p-4 text-sm space-y-1 ${sagaResult.success ? "bg-emerald-50 border border-emerald-200" : "bg-red-50 border border-red-200"}`}>
                            <div className="flex items-center gap-2 font-semibold">
                                {sagaResult.success ? (
                                    <span className="text-emerald-700">Saga 완료</span>
                                ) : (
                                    <span className="text-red-700">Saga 실패 (보상 트랜잭션 실행됨)</span>
                                )}
                            </div>
                            <div className="text-slate-600">Saga ID: <span className="font-mono text-xs">{sagaResult.sagaId}</span></div>
                            {sagaResult.resumeId && <div className="text-slate-600">Resume ID: {sagaResult.resumeId}</div>}
                            {sagaResult.fileKey && <div className="text-slate-600">S3 Key: <span className="font-mono text-xs">{sagaResult.fileKey}</span></div>}
                            {sagaResult.errorMessage && <div className="text-red-600">{sagaResult.errorMessage}</div>}
                        </div>
                    )}
                </div>

                {/* Saga 스텝 진행 상태 */}
                {sagaState && (
                    <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
                        <div className="flex items-center justify-between">
                            <h2 className="font-semibold text-slate-800">Saga 상태 추적</h2>
                            <span className={`text-xs font-bold px-2 py-1 rounded-full ${statusConfig[sagaState.status].bg} ${statusConfig[sagaState.status].color}`}>
                                {statusConfig[sagaState.status].label}
                            </span>
                        </div>
                        <p className="text-xs text-slate-500 font-mono">ID: {sagaState.sagaId}</p>

                        {/* 스텝 인디케이터 */}
                        <div className="flex items-center gap-0 mt-2">
                            {steps.map((step, idx) => {
                                const state = getStepDone(sagaState.status, step.key);
                                return (
                                    <div key={step.key} className="flex items-center flex-1">
                                        <div className="flex flex-col items-center flex-1">
                                            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all
                                                ${state === "done" ? "bg-emerald-500 text-white" :
                                                  state === "active" ? "bg-blue-500 text-white animate-pulse" :
                                                  state === "error" ? "bg-red-400 text-white" :
                                                  "bg-slate-200 text-slate-400"}`}>
                                                {state === "done" ? "✓" : state === "error" ? "✗" : idx + 1}
                                            </div>
                                            <span className={`text-xs mt-1 font-medium ${state === "done" ? "text-emerald-600" : state === "active" ? "text-blue-600" : state === "error" ? "text-red-500" : "text-slate-400"}`}>
                                                {step.label}
                                            </span>
                                        </div>
                                        {idx < steps.length - 1 && (
                                            <div className={`h-0.5 flex-1 mx-1 ${state === "done" ? "bg-emerald-400" : "bg-slate-200"}`} />
                                        )}
                                    </div>
                                );
                            })}
                        </div>

                        {(sagaState.status === "COMPENSATED" || sagaState.status === "COMPENSATING") && (
                            <div className="bg-orange-50 border border-orange-200 rounded-lg p-3 text-sm text-orange-700">
                                DB 저장 실패 감지 → S3 업로드된 파일 자동 삭제(보상 트랜잭션) 실행됨
                            </div>
                        )}
                        {sagaState.errorMessage && (
                            <div className="text-xs text-slate-500 bg-slate-50 rounded p-2 font-mono">{sagaState.errorMessage}</div>
                        )}
                    </div>
                )}

                {/* 특정 Saga 조회 */}
                <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
                    <h2 className="font-semibold text-slate-800">Saga 상태 조회</h2>
                    <div className="flex gap-2">
                        <input
                            type="text"
                            placeholder="Saga ID 입력"
                            value={queryId}
                            onChange={(e) => setQueryId(e.target.value)}
                            className="flex-1 border border-slate-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-300"
                        />
                        <button
                            onClick={() => fetchSagaState(queryId)}
                            className="px-4 py-2 bg-slate-800 text-white rounded-lg text-sm hover:bg-slate-900 transition"
                        >
                            조회
                        </button>
                    </div>
                </div>

                {/* 활성 Saga 목록 */}
                <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
                    <div className="flex items-center justify-between">
                        <h2 className="font-semibold text-slate-800">활성 Saga 키 목록 (Redis)</h2>
                        <button
                            onClick={fetchActiveKeys}
                            className="text-xs text-blue-600 hover:underline"
                        >
                            새로고침
                        </button>
                    </div>
                    {activeKeys.length === 0 ? (
                        <p className="text-sm text-slate-400">활성 Saga 없음 (새로고침을 눌러 확인)</p>
                    ) : (
                        <ul className="space-y-1">
                            {activeKeys.map((k) => (
                                <li key={k} className="text-xs font-mono text-slate-600 bg-slate-50 rounded px-3 py-1.5 cursor-pointer hover:bg-blue-50"
                                    onClick={() => {
                                        const id = k.replace("saga:resume:", "");
                                        setQueryId(id);
                                        fetchSagaState(id);
                                    }}>
                                    {k}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                {/* 아키텍처 설명 */}
                <div className="bg-slate-800 rounded-xl p-6 text-slate-300 text-sm space-y-3">
                    <h2 className="text-white font-semibold">Saga 패턴 동작 원리</h2>
                    <div className="space-y-2">
                        <div className="flex gap-3">
                            <span className="text-blue-400 font-mono text-xs w-6 shrink-0">1</span>
                            <span>S3 파일 업로드 → 성공 시 fileKey를 Saga 상태에 기록</span>
                        </div>
                        <div className="flex gap-3">
                            <span className="text-blue-400 font-mono text-xs w-6 shrink-0">2</span>
                            <span>DB 저장 → 실패 시 <span className="text-orange-300">보상 트랜잭션</span>으로 S3 파일 자동 삭제</span>
                        </div>
                        <div className="flex gap-3">
                            <span className="text-blue-400 font-mono text-xs w-6 shrink-0">3</span>
                            <span>ES 인덱싱 → 실패 시 성공으로 처리 후 <span className="text-yellow-300">재시도 큐</span>에 등록 (Eventually Consistent)</span>
                        </div>
                        <div className="flex gap-3">
                            <span className="text-blue-400 font-mono text-xs w-6 shrink-0">4</span>
                            <span>모든 Saga 상태는 Redis에 <span className="text-emerald-300">24시간 TTL</span>로 추적</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
