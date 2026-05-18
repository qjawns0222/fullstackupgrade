"use client";

import { useQuery } from "@tanstack/react-query";

interface VaultStatus {
  enabled: boolean;
  mode: string;
  uri: string | null;
  kvBackend: string;
  applicationName: string;
  healthy: boolean;
  details: Record<string, string>;
}

interface SecretEntry {
  key: string;
  description: string;
  managedByVault: boolean;
}

interface SecretsManifest {
  secrets: SecretEntry[];
}

const fetchVaultStatus = async (): Promise<VaultStatus> => {
  const res = await fetch("/api/vault/status");
  if (!res.ok) throw new Error("Failed to fetch vault status");
  return res.json();
};

const fetchSecretsManifest = async (): Promise<SecretsManifest> => {
  const res = await fetch("/api/vault/secrets/manifest");
  if (!res.ok) throw new Error("Failed to fetch secrets manifest");
  return res.json();
};

export default function VaultPage() {
  const { data: status, isLoading: statusLoading } = useQuery({
    queryKey: ["vault-status"],
    queryFn: fetchVaultStatus,
    refetchInterval: 5000,
  });

  const { data: manifest, isLoading: manifestLoading } = useQuery({
    queryKey: ["vault-manifest"],
    queryFn: fetchSecretsManifest,
    refetchInterval: 5000,
  });

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">Secret Vault 관리</h1>

      {/* 상태 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <StatCard
          title="Vault 모드"
          value={statusLoading ? "..." : (status?.mode ?? "-")}
          color={status?.enabled ? "blue" : "gray"}
        />
        <StatCard
          title="연결 상태"
          value={statusLoading ? "..." : status?.healthy ? "정상" : "오류"}
          color={status?.healthy ? "green" : "red"}
        />
        <StatCard
          title="시크릿 수"
          value={manifestLoading ? "..." : String(manifest?.secrets.length ?? 0)}
          color="purple"
        />
      </div>

      {/* Vault 상세 정보 */}
      {status && (
        <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Vault 연결 정보</h2>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <dt className="text-gray-500">모드</dt>
            <dd className="font-mono">{status.mode}</dd>
            {status.uri && (
              <>
                <dt className="text-gray-500">URI</dt>
                <dd className="font-mono">{status.uri}</dd>
              </>
            )}
            <dt className="text-gray-500">KV Backend</dt>
            <dd className="font-mono">{status.kvBackend}</dd>
            <dt className="text-gray-500">Application</dt>
            <dd className="font-mono">{status.applicationName}</dd>
          </dl>
          {Object.keys(status.details).length > 0 && (
            <div className="mt-4 pt-4 border-t border-gray-100">
              <p className="text-xs text-gray-400 mb-2">Details</p>
              <dl className="grid grid-cols-2 gap-2 text-xs">
                {Object.entries(status.details).map(([k, v]) => (
                  <div key={k} className="contents">
                    <dt className="text-gray-500">{k}</dt>
                    <dd className="font-mono text-gray-700">{v}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
        </div>
      )}

      {/* 시크릿 목록 */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h2 className="text-lg font-semibold mb-4">관리 시크릿 목록</h2>
        {manifestLoading ? (
          <p className="text-gray-400 text-sm">로딩 중...</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="text-left py-2 text-gray-500 font-medium">키</th>
                <th className="text-left py-2 text-gray-500 font-medium">설명</th>
                <th className="text-center py-2 text-gray-500 font-medium">관리 주체</th>
              </tr>
            </thead>
            <tbody>
              {manifest?.secrets.map((s) => (
                <tr key={s.key} className="border-b border-gray-50">
                  <td className="py-2 font-mono text-xs text-gray-700">{s.key}</td>
                  <td className="py-2 text-gray-600">{s.description}</td>
                  <td className="py-2 text-center">
                    <span
                      className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                        s.managedByVault
                          ? "bg-blue-100 text-blue-700"
                          : "bg-gray-100 text-gray-500"
                      }`}
                    >
                      {s.managedByVault ? "Vault" : "application.yml"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 로컬 개발 가이드 */}
      {!status?.enabled && (
        <div className="mt-6 bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-sm">
          <p className="font-semibold text-yellow-800 mb-1">로컬 개발 모드</p>
          <p className="text-yellow-700">
            Vault가 비활성화되어 있습니다. 프로덕션에서는{" "}
            <code className="bg-yellow-100 px-1 rounded">--spring.profiles.active=vault</code>
            과 환경변수 <code className="bg-yellow-100 px-1 rounded">VAULT_URI</code>,{" "}
            <code className="bg-yellow-100 px-1 rounded">VAULT_TOKEN</code>을 설정하세요.
          </p>
        </div>
      )}
    </div>
  );
}

function StatCard({
  title,
  value,
  color,
}: {
  title: string;
  value: string;
  color: "blue" | "green" | "red" | "gray" | "purple";
}) {
  const colors = {
    blue: "bg-blue-50 text-blue-700 border-blue-200",
    green: "bg-green-50 text-green-700 border-green-200",
    red: "bg-red-50 text-red-700 border-red-200",
    gray: "bg-gray-50 text-gray-600 border-gray-200",
    purple: "bg-purple-50 text-purple-700 border-purple-200",
  };
  return (
    <div className={`rounded-lg border p-4 ${colors[color]}`}>
      <p className="text-xs font-medium opacity-70 mb-1">{title}</p>
      <p className="text-2xl font-bold">{value}</p>
    </div>
  );
}
