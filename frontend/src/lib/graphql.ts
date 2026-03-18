import api from '@/lib/axios';

export interface GraphQlResponse<T> {
    data: T;
    errors?: { message: string; path?: string[] }[];
}

export async function gql<T>(
    query: string,
    variables?: Record<string, unknown>
): Promise<T> {
    const res = await api.post<GraphQlResponse<T>>('/graphql', { query, variables }, {
        baseURL: process.env.NEXT_PUBLIC_API_URL?.replace('/api', '') || 'http://localhost:8000',
    });
    if (res.data.errors?.length) {
        throw new Error(res.data.errors[0].message);
    }
    return res.data.data;
}
