'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface RotationHistory {
  id: number;
  rotatedAt: string;
  keyCount: number;
  status: 'SUCCESS' | 'FAILED';
  errorMessage: string | null;
}

interface EncryptionStatus {
  algorithm: string;
  keyRotationEnabled: boolean;
  lastRotatedAt: string | null;
  totalRotations: number;
  failedRotations: number;
  history: RotationHistory[];
}

interface VerifyResponse {
  original: string;
  encrypted: string;
  decrypted: string;
  success: boolean;
}

export default function EncryptionDashboardPage() {
  const queryClient = useQueryClient();
  const [verifyInput, setVerifyInput] = useState('');
  const [verifyResult, setVerifyResult] = useState<VerifyResponse | null>(null);

  const { data: status, isLoading } = useQuery<EncryptionStatus>({
    queryKey: ['encryption-status'],
    queryFn: () => fetch('/api/encryption/status').then((r) => r.json()),
    refetchInterval: 10000,
  });

  const rotateMutation = useMutation({
    mutationFn: () =>
      fetch('/api/encryption/rotate', { method: 'POST' }).then((r) => r.json()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['encryption-status'] }),
  });

  const verifyMutation = useMutation({
    mutationFn: (plaintext: string) =>
      fetch('/api/encryption/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ plaintext }),
      }).then((r) => r.json()),
    onSuccess: (data) => setVerifyResult(data),
  });

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">암호화 관리</h1>
      <p className="text-sm text-gray-500">Google Tink AES-256-GCM 기반 필드 레벨 암호화 및 키 로테이션</p>

      {/* 상태 카드 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="알고리즘" value={status?.algorithm ?? '-'} color="bg-blue-50 text-blue-700" />
        <StatCard label="키 로테이션" value={status?.keyRotationEnabled ? '활성화' : '비활성화'} color="bg-green-50 text-green-700" />
        <StatCard label="총 로테이션" value={String(status?.totalRotations ?? 0)} color="bg-purple-50 text-purple-700" />
        <StatCard label="실패 횟수" value={String(status?.failedRotations ?? 0)} color={status?.failedRotations ? 'bg-red-50 text-red-700' : 'bg-gray-50 text-gray-600'} />
      </div>

      {/* 마지막 로테이션 */}
      <div className="bg-white border rounded-lg p-4 flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500">마지막 키 로테이션</p>
          <p className="text-base font-medium text-gray-800">
            {status?.lastRotatedAt ? new Date(status.lastRotatedAt).toLocaleString('ko-KR') : '기록 없음'}
          </p>
          <p className="text-xs text-gray-400 mt-0.5">매일 새벽 2시 자동 실행</p>
        </div>
        <button
          onClick={() => rotateMutation.mutate()}
          disabled={rotateMutation.isPending}
          className="px-4 py-2 bg-indigo-600 text-white text-sm rounded-md hover:bg-indigo-700 disabled:opacity-50 transition"
        >
          {rotateMutation.isPending ? '로테이션 중...' : '지금 로테이션'}
        </button>
      </div>

      {rotateMutation.isSuccess && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 text-sm text-green-700">
          키 로테이션 완료. 새 키가 primary로 승격되었습니다.
        </div>
      )}

      {/* 암호화 검증 */}
      <div className="bg-white border rounded-lg p-4 space-y-3">
        <h2 className="text-base font-semibold text-gray-800">암호화 검증</h2>
        <div className="flex gap-2">
          <input
            type="text"
            value={verifyInput}
            onChange={(e) => setVerifyInput(e.target.value)}
            placeholder="평문 입력 (예: test@example.com)"
            className="flex-1 border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
          />
          <button
            onClick={() => verifyMutation.mutate(verifyInput)}
            disabled={!verifyInput || verifyMutation.isPending}
            className="px-4 py-2 bg-gray-800 text-white text-sm rounded-md hover:bg-gray-700 disabled:opacity-50 transition"
          >
            검증
          </button>
        </div>
        {verifyResult && (
          <div className="space-y-2 text-xs font-mono bg-gray-50 rounded-md p-3">
            <div><span className="text-gray-500">원문: </span><span className="text-gray-800">{verifyResult.original}</span></div>
            <div><span className="text-gray-500">암호화: </span><span className="text-blue-700 break-all">{verifyResult.encrypted}</span></div>
            <div><span className="text-gray-500">복호화: </span><span className="text-gray-800">{verifyResult.decrypted}</span></div>
            <div className={`font-semibold ${verifyResult.success ? 'text-green-600' : 'text-red-600'}`}>
              {verifyResult.success ? '✓ 암복호화 정상' : '✗ 암복호화 실패'}
            </div>
          </div>
        )}
      </div>

      {/* 로테이션 히스토리 */}
      <div className="bg-white border rounded-lg overflow-hidden">
        <div className="px-4 py-3 border-b bg-gray-50">
          <h2 className="text-base font-semibold text-gray-800">로테이션 히스토리</h2>
        </div>
        {isLoading ? (
          <div className="p-6 text-center text-sm text-gray-400">로딩 중...</div>
        ) : status?.history?.length === 0 ? (
          <div className="p-6 text-center text-sm text-gray-400">로테이션 기록이 없습니다.</div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="px-4 py-2 text-left">일시</th>
                <th className="px-4 py-2 text-left">키 수</th>
                <th className="px-4 py-2 text-left">상태</th>
                <th className="px-4 py-2 text-left">오류</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {status?.history?.map((h) => (
                <tr key={h.id} className="hover:bg-gray-50">
                  <td className="px-4 py-2 text-gray-700">
                    {new Date(h.rotatedAt).toLocaleString('ko-KR')}
                  </td>
                  <td className="px-4 py-2 text-gray-700">{h.keyCount}</td>
                  <td className="px-4 py-2">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      h.status === 'SUCCESS' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                    }`}>
                      {h.status}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-red-600 text-xs truncate max-w-xs">
                    {h.errorMessage ?? '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className={`rounded-lg p-4 ${color}`}>
      <p className="text-xs font-medium opacity-70">{label}</p>
      <p className="text-xl font-bold mt-1">{value}</p>
    </div>
  );
}
