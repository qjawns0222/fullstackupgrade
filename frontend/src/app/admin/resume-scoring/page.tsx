'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface ResumeScore {
  id: number;
  analysisRequestId: number;
  jobTitle: string;
  totalScore: number;
  skillScore: number;
  experienceScore: number;
  educationScore: number;
  extractedSkills: string | null;
  extractedExperience: string | null;
  extractedEducation: string | null;
  summary: string | null;
  createdAt: string;
}

interface ScoringRequest {
  analysisRequestId: number;
  resumeText: string;
  jobTitle: string;
}

const SCORE_COLOR = (score: number) => {
  if (score >= 80) return 'text-green-600';
  if (score >= 60) return 'text-yellow-600';
  return 'text-red-500';
};

const ScoreBar = ({ score }: { score: number }) => (
  <div className="flex items-center gap-2">
    <div className="flex-1 bg-gray-100 rounded-full h-2">
      <div
        className={`h-2 rounded-full ${score >= 80 ? 'bg-green-500' : score >= 60 ? 'bg-yellow-400' : 'bg-red-400'}`}
        style={{ width: `${score}%` }}
      />
    </div>
    <span className={`text-sm font-bold w-8 text-right ${SCORE_COLOR(score)}`}>{score}</span>
  </div>
);

export default function ResumeScoringPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<ScoringRequest>({ analysisRequestId: 1, resumeText: '', jobTitle: '' });
  const [selected, setSelected] = useState<ResumeScore | null>(null);

  const { data: scores, isLoading } = useQuery<ResumeScore[]>({
    queryKey: ['resume-scores'],
    queryFn: () => fetch('/api/scoring').then((r) => r.json()),
    refetchInterval: 5000,
  });

  const scoreMutation = useMutation({
    mutationFn: (req: ScoringRequest) =>
      fetch('/api/scoring', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
      }).then((r) => r.json()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['resume-scores'] });
      setForm((f) => ({ ...f, resumeText: '', jobTitle: '' }));
    },
  });

  const stats = scores
    ? {
        total: scores.length,
        avgTotal: scores.length ? Math.round(scores.reduce((s, r) => s + r.totalScore, 0) / scores.length) : 0,
        high: scores.filter((r) => r.totalScore >= 80).length,
        low: scores.filter((r) => r.totalScore < 60).length,
      }
    : null;

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">AI 이력서 스코어링</h1>
        <p className="text-sm text-gray-500 mt-1">GPT-4o-mini가 이력서를 분석해 직무 적합도 0-100 점수를 산출합니다.</p>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-4 gap-4 mb-6">
          {[
            { label: '총 분석', value: stats.total, color: 'text-gray-800' },
            { label: '평균 점수', value: stats.avgTotal, color: 'text-blue-600' },
            { label: '우수 (80+)', value: stats.high, color: 'text-green-600' },
            { label: '미달 (-60)', value: stats.low, color: 'text-red-500' },
          ].map(({ label, value, color }) => (
            <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
              <p className="text-xs text-gray-500">{label}</p>
              <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-3 gap-6">
        {/* Form */}
        <div className="col-span-1 bg-white border rounded-lg p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">새 분석 요청</h2>
          <div className="space-y-3">
            <div>
              <label className="text-xs text-gray-500 block mb-1">분석 요청 ID</label>
              <input
                type="number"
                value={form.analysisRequestId}
                onChange={(e) => setForm((f) => ({ ...f, analysisRequestId: Number(e.target.value) }))}
                className="w-full border rounded px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="text-xs text-gray-500 block mb-1">직무명</label>
              <input
                type="text"
                placeholder="예: Backend Engineer"
                value={form.jobTitle}
                onChange={(e) => setForm((f) => ({ ...f, jobTitle: e.target.value }))}
                className="w-full border rounded px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="text-xs text-gray-500 block mb-1">이력서 텍스트</label>
              <textarea
                rows={8}
                placeholder="OCR 추출 텍스트 또는 이력서 내용을 붙여넣으세요..."
                value={form.resumeText}
                onChange={(e) => setForm((f) => ({ ...f, resumeText: e.target.value }))}
                className="w-full border rounded px-3 py-2 text-sm resize-none"
              />
            </div>
            <button
              onClick={() => scoreMutation.mutate(form)}
              disabled={scoreMutation.isPending || !form.resumeText.trim() || !form.jobTitle.trim()}
              className="w-full py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {scoreMutation.isPending ? '분석 중...' : 'AI 스코어링 실행'}
            </button>
            {scoreMutation.isError && (
              <p className="text-xs text-red-500">분석 실패. 다시 시도해주세요.</p>
            )}
          </div>
        </div>

        {/* Score List */}
        <div className="col-span-2 space-y-3">
          {isLoading ? (
            <div className="text-center py-10 text-gray-400 text-sm">로딩 중...</div>
          ) : scores?.length === 0 ? (
            <div className="text-center py-10 text-gray-400 text-sm">분석 결과 없음</div>
          ) : (
            scores?.map((score) => (
              <div
                key={score.id}
                onClick={() => setSelected(selected?.id === score.id ? null : score)}
                className="bg-white border rounded-lg p-4 shadow-sm cursor-pointer hover:border-blue-300 transition-colors"
              >
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <span className="text-xs text-gray-400 font-mono">req#{score.analysisRequestId}</span>
                    <h3 className="font-semibold text-gray-900">{score.jobTitle}</h3>
                    <p className="text-xs text-gray-400">{score.createdAt?.replace('T', ' ').slice(0, 19)}</p>
                  </div>
                  <div className={`text-3xl font-bold ${SCORE_COLOR(score.totalScore)}`}>
                    {score.totalScore}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center gap-2 text-xs text-gray-500">
                    <span className="w-16">스킬</span>
                    <ScoreBar score={score.skillScore} />
                  </div>
                  <div className="flex items-center gap-2 text-xs text-gray-500">
                    <span className="w-16">경력</span>
                    <ScoreBar score={score.experienceScore} />
                  </div>
                  <div className="flex items-center gap-2 text-xs text-gray-500">
                    <span className="w-16">학력</span>
                    <ScoreBar score={score.educationScore} />
                  </div>
                </div>

                {selected?.id === score.id && (
                  <div className="mt-4 pt-4 border-t space-y-2 text-sm text-gray-700">
                    {score.summary && (
                      <p className="italic text-gray-600 bg-gray-50 rounded p-2">{score.summary}</p>
                    )}
                    {score.extractedSkills && (
                      <div>
                        <span className="text-xs font-semibold text-gray-500 uppercase">스킬</span>
                        <p className="mt-0.5">{score.extractedSkills}</p>
                      </div>
                    )}
                    {score.extractedExperience && (
                      <div>
                        <span className="text-xs font-semibold text-gray-500 uppercase">경력 요약</span>
                        <p className="mt-0.5">{score.extractedExperience}</p>
                      </div>
                    )}
                    {score.extractedEducation && (
                      <div>
                        <span className="text-xs font-semibold text-gray-500 uppercase">학력 요약</span>
                        <p className="mt-0.5">{score.extractedEducation}</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
