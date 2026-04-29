'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

type NotificationChannel = 'STOMP' | 'GRAPHQL' | 'WEBHOOK' | 'EMAIL';

interface NotificationPreference {
  channel: NotificationChannel;
  enabled: boolean;
  updatedAt: string;
}

const CHANNEL_LABELS: Record<NotificationChannel, { label: string; desc: string }> = {
  STOMP: { label: 'WebSocket (STOMP)', desc: '브라우저 실시간 알림' },
  GRAPHQL: { label: 'GraphQL Subscription', desc: 'GraphQL 구독 스트림' },
  WEBHOOK: { label: 'Webhook', desc: '외부 엔드포인트 HTTP POST' },
  EMAIL: { label: 'Email', desc: '이메일 비동기 발송' },
};

async function fetchPreferences(): Promise<NotificationPreference[]> {
  const res = await fetch('/api/notifications/preferences');
  if (!res.ok) throw new Error('Failed to fetch preferences');
  return res.json();
}

async function updatePreference(channel: NotificationChannel, enabled: boolean): Promise<NotificationPreference> {
  const res = await fetch('/api/notifications/preferences', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channel, enabled }),
  });
  if (!res.ok) throw new Error('Failed to update preference');
  return res.json();
}

async function deletePreference(channel: NotificationChannel): Promise<void> {
  const res = await fetch(`/api/notifications/preferences/${channel}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete preference');
}

export default function NotificationHubPage() {
  const queryClient = useQueryClient();
  const [testMessage, setTestMessage] = useState('');
  const [testResult, setTestResult] = useState<string | null>(null);

  const { data: preferences, isLoading } = useQuery<NotificationPreference[]>({
    queryKey: ['notification-preferences'],
    queryFn: fetchPreferences,
    refetchInterval: 5000,
  });

  const upsertMutation = useMutation({
    mutationFn: ({ channel, enabled }: { channel: NotificationChannel; enabled: boolean }) =>
      updatePreference(channel, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notification-preferences'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (channel: NotificationChannel) => deletePreference(channel),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notification-preferences'] }),
  });

  const handleToggle = (channel: NotificationChannel, currentEnabled: boolean) => {
    upsertMutation.mutate({ channel, enabled: !currentEnabled });
  };

  const handleTest = async () => {
    try {
      const res = await fetch('/api/notifications/preferences/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'TEST', title: '테스트 알림', message: testMessage || '알림 허브 테스트' }),
      });
      setTestResult(res.ok ? '✓ 발송 요청 완료' : '✗ 발송 실패');
    } catch {
      setTestResult('✗ 네트워크 오류');
    }
  };

  const enabledCount = preferences?.filter((p) => p.enabled).length ?? 0;

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">알림 허브</h1>
        <p className="text-sm text-gray-500 mt-1">
          채널별 알림 수신 선호도를 설정합니다. NotificationRouter가 활성 채널로 자동 라우팅합니다.
        </p>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">활성 채널</p>
          <p className="text-2xl font-bold text-green-600">{enabledCount}</p>
        </div>
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">전체 채널</p>
          <p className="text-2xl font-bold text-gray-700">{preferences?.length ?? 0}</p>
        </div>
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <p className="text-xs text-gray-500">기본 채널</p>
          <p className="text-2xl font-bold text-blue-600">STOMP</p>
        </div>
      </div>

      {/* 채널 선호도 목록 */}
      <div className="bg-white border rounded-lg shadow-sm mb-6">
        <div className="p-4 border-b">
          <h2 className="text-sm font-semibold text-gray-700">채널 선호도 설정</h2>
        </div>
        {isLoading ? (
          <div className="p-4 text-sm text-gray-400">불러오는 중...</div>
        ) : (
          <div className="divide-y">
            {(preferences ?? []).map((pref) => {
              const info = CHANNEL_LABELS[pref.channel];
              return (
                <div key={pref.channel} className="flex items-center justify-between px-4 py-3">
                  <div>
                    <p className="text-sm font-medium text-gray-800">{info.label}</p>
                    <p className="text-xs text-gray-400">{info.desc}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${pref.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                      {pref.enabled ? '활성' : '비활성'}
                    </span>
                    <button
                      onClick={() => handleToggle(pref.channel, pref.enabled)}
                      className={`text-xs px-3 py-1 rounded border ${pref.enabled ? 'border-red-300 text-red-600 hover:bg-red-50' : 'border-green-300 text-green-600 hover:bg-green-50'}`}
                    >
                      {pref.enabled ? '비활성화' : '활성화'}
                    </button>
                    <button
                      onClick={() => deleteMutation.mutate(pref.channel)}
                      className="text-xs px-3 py-1 rounded border border-gray-300 text-gray-500 hover:bg-gray-50"
                    >
                      초기화
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 테스트 발송 */}
      <div className="bg-white border rounded-lg shadow-sm p-4">
        <h2 className="text-sm font-semibold text-gray-700 mb-3">알림 테스트 발송</h2>
        <div className="flex gap-2">
          <input
            type="text"
            value={testMessage}
            onChange={(e) => setTestMessage(e.target.value)}
            placeholder="테스트 메시지 입력..."
            className="flex-1 text-sm border rounded px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-blue-400"
          />
          <button
            onClick={handleTest}
            className="text-sm px-4 py-1.5 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            발송
          </button>
        </div>
        {testResult && (
          <p className={`mt-2 text-xs ${testResult.startsWith('✓') ? 'text-green-600' : 'text-red-600'}`}>
            {testResult}
          </p>
        )}
      </div>
    </div>
  );
}
