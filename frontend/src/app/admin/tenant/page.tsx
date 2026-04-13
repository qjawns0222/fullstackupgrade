"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

interface Tenant {
  id: number;
  tenantId: string;
  name: string;
  schemaName: string;
  status: "ACTIVE" | "SUSPENDED" | "DELETED";
  createdAt: string;
  updatedAt: string;
}

interface TenantStats {
  total: number;
  active: number;
  suspended: number;
  deleted: number;
}

const BASE = "http://localhost:8080/api/tenants";

async function fetchTenants(): Promise<Tenant[]> {
  const res = await fetch(BASE);
  if (!res.ok) throw new Error("Failed to fetch tenants");
  return res.json();
}

async function fetchStats(): Promise<TenantStats> {
  const res = await fetch(`${BASE}/stats`);
  if (!res.ok) throw new Error("Failed to fetch stats");
  return res.json();
}

async function createTenant(tenantId: string, name: string): Promise<Tenant> {
  const res = await fetch(BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tenantId, name }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function updateTenantStatus(
  tenantId: string,
  action: "suspend" | "activate"
): Promise<Tenant> {
  const res = await fetch(`${BASE}/${tenantId}/${action}`, { method: "PUT" });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function deleteTenant(tenantId: string): Promise<Tenant> {
  const res = await fetch(`${BASE}/${tenantId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-800",
  SUSPENDED: "bg-yellow-100 text-yellow-800",
  DELETED: "bg-red-100 text-red-800",
};

export default function TenantPage() {
  const qc = useQueryClient();
  const [newTenantId, setNewTenantId] = useState("");
  const [newName, setNewName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ["tenants"],
    queryFn: fetchTenants,
    refetchInterval: 5000,
  });

  const { data: stats } = useQuery({
    queryKey: ["tenantStats"],
    queryFn: fetchStats,
    refetchInterval: 5000,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["tenants"] });
    qc.invalidateQueries({ queryKey: ["tenantStats"] });
  };

  const createMutation = useMutation({
    mutationFn: () => createTenant(newTenantId.trim(), newName.trim()),
    onSuccess: () => {
      setNewTenantId("");
      setNewName("");
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  const statusMutation = useMutation({
    mutationFn: ({
      tenantId,
      action,
    }: {
      tenantId: string;
      action: "suspend" | "activate";
    }) => updateTenantStatus(tenantId, action),
    onSuccess: invalidate,
    onError: (e: Error) => setError(e.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (tenantId: string) => deleteTenant(tenantId),
    onSuccess: invalidate,
    onError: (e: Error) => setError(e.message),
  });

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">멀티 테넌트 관리</h1>

      {/* 통계 카드 */}
      {stats && (
        <div className="grid grid-cols-4 gap-4">
          {[
            { label: "전체", value: stats.total, color: "bg-blue-50 text-blue-800" },
            { label: "활성", value: stats.active, color: "bg-green-50 text-green-800" },
            { label: "정지", value: stats.suspended, color: "bg-yellow-50 text-yellow-800" },
            { label: "삭제", value: stats.deleted, color: "bg-red-50 text-red-800" },
          ].map(({ label, value, color }) => (
            <div key={label} className={`rounded-lg p-4 ${color}`}>
              <div className="text-sm font-medium">{label}</div>
              <div className="text-3xl font-bold mt-1">{value}</div>
            </div>
          ))}
        </div>
      )}

      {/* 테넌트 생성 폼 */}
      <div className="bg-white border rounded-lg p-4 space-y-3">
        <h2 className="font-semibold text-gray-700">새 테넌트 등록</h2>
        <div className="flex gap-2">
          <input
            className="border rounded px-3 py-2 text-sm flex-1"
            placeholder="테넌트 ID (예: acme-corp)"
            value={newTenantId}
            onChange={(e) => setNewTenantId(e.target.value)}
          />
          <input
            className="border rounded px-3 py-2 text-sm flex-1"
            placeholder="테넌트 이름"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <button
            className="bg-blue-600 text-white px-4 py-2 rounded text-sm disabled:opacity-50"
            disabled={!newTenantId.trim() || !newName.trim() || createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            {createMutation.isPending ? "생성 중..." : "생성"}
          </button>
        </div>
        {error && <p className="text-red-600 text-sm">{error}</p>}
      </div>

      {/* 테넌트 목록 */}
      <div className="bg-white border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-3 text-left">테넌트 ID</th>
              <th className="px-4 py-3 text-left">이름</th>
              <th className="px-4 py-3 text-left">스키마</th>
              <th className="px-4 py-3 text-left">상태</th>
              <th className="px-4 py-3 text-left">생성일</th>
              <th className="px-4 py-3 text-left">액션</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {isLoading ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  로딩 중...
                </td>
              </tr>
            ) : tenants.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  등록된 테넌트가 없습니다.
                </td>
              </tr>
            ) : (
              tenants.map((t) => (
                <tr key={t.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono font-medium">{t.tenantId}</td>
                  <td className="px-4 py-3">{t.name}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{t.schemaName}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${STATUS_COLOR[t.status]}`}>
                      {t.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {new Date(t.createdAt).toLocaleDateString("ko-KR")}
                  </td>
                  <td className="px-4 py-3 flex gap-2">
                    {t.status === "ACTIVE" && (
                      <button
                        className="text-xs px-2 py-1 bg-yellow-100 text-yellow-700 rounded hover:bg-yellow-200"
                        onClick={() => statusMutation.mutate({ tenantId: t.tenantId, action: "suspend" })}
                      >
                        정지
                      </button>
                    )}
                    {t.status === "SUSPENDED" && (
                      <button
                        className="text-xs px-2 py-1 bg-green-100 text-green-700 rounded hover:bg-green-200"
                        onClick={() => statusMutation.mutate({ tenantId: t.tenantId, action: "activate" })}
                      >
                        활성화
                      </button>
                    )}
                    {t.status !== "DELETED" && (
                      <button
                        className="text-xs px-2 py-1 bg-red-100 text-red-700 rounded hover:bg-red-200"
                        onClick={() => deleteMutation.mutate(t.tenantId)}
                      >
                        삭제
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-gray-400">
        * X-Tenant-ID 헤더로 테넌트를 지정합니다. 헤더 없으면 &quot;default&quot; 테넌트로 라우팅됩니다.
      </p>
    </div>
  );
}
