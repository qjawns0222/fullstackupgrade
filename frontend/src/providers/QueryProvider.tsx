'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';

// TODO: 백엔드 searchConditionCache 값과 동기화 필요
// 백엔드 파일을 확인할 수 없어 임의의 값(예: 60초)으로 설정함.
const SEARCH_CONDITION_CACHE_TIME = 60 * 1000;

export default function QueryProvider({ children }: { children: React.ReactNode }) {
    const [queryClient] = useState(
        () =>
            new QueryClient({
                defaultOptions: {
                    queries: {
                        // 백엔드 캐시 시간과 동기화하여 불필요한 요청 방지
                        staleTime: SEARCH_CONDITION_CACHE_TIME,
                        // 기본 gcTime은 staleTime보다 길어야 안전함
                        gcTime: SEARCH_CONDITION_CACHE_TIME + 5 * 60 * 1000,
                        refetchOnWindowFocus: false,
                    },
                },
            })
    );

    return (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    );
}
