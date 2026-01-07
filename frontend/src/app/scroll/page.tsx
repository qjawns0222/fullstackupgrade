'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useInView } from 'react-intersection-observer';
import { User } from '@/types/User';

// 임시 Mock 데이터 생성 함수
const fetchMockUsers = async (page: number): Promise<User[]> => {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(
                Array.from({ length: 10 }).map((_, i) => ({
                    id: (page - 1) * 10 + i,
                    name: `User ${page}-${i}`,
                    password: 'hashed_password', // Mock data
                    role: 'user'
                }))
            );
        }, 1000);
    });
};

export default function Scroll() {
    const { ref, inView } = useInView();
    const [users, setUsers] = useState<User[]>([]);
    const [page, setPage] = useState(1);
    const [loading, setLoading] = useState(false);
    const [hasMore, setHasMore] = useState(true);

    const loadMoreUsers = useCallback(async () => {
        if (loading || !hasMore) return;

        setLoading(true);
        try {
            const newUsers = await fetchMockUsers(page);
            if (newUsers.length === 0) {
                setHasMore(false);
            } else {
                setUsers(prev => [...prev, ...newUsers]);
                setPage(prev => prev + 1);
            }
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    }, [page, loading, hasMore]);

    // 초기 로딩
    useEffect(() => {
        loadMoreUsers();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // 스크롤 감지 시 로딩
    useEffect(() => {
        if (inView && hasMore) {
            loadMoreUsers();
        }
    }, [inView, hasMore, loadMoreUsers]);

    return (
        <div className="max-w-md mx-auto p-4 space-y-4">
            <h1 className="text-xl font-bold mb-4">User List (Infinite Scroll)</h1>

            <div className="space-y-4">
                {users.map((user, index) => (
                    <div key={`${user.id}-${index}`} className="p-4 bg-white border rounded-lg shadow-sm">
                        <h3 className="font-bold text-gray-800">{user.name}</h3>
                        <p className="text-sm text-gray-500">Role: {user.role}</p>
                    </div>
                ))}
            </div>

            <div ref={ref} className="mt-4 py-4 w-full flex items-center justify-center min-h-[50px]">
                {loading && (
                    <div className="w-full h-12 bg-gray-100 animate-pulse rounded flex items-center justify-center">
                        <span className="text-gray-400 text-sm">Loading more...</span>
                    </div>
                )}
                {!hasMore && <p className="text-sm text-gray-500">모든 데이터를 불러왔습니다.</p>}
            </div>
        </div>
    );
}