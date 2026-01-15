'use client';

import React from 'react';
import { useForm, SubmitHandler } from 'react-hook-form';
import api from '@/lib/axios';
import { LoginFormInputs } from '@/types/LoginFormInputs';
import { useRouter } from 'next/navigation';

export default function LoginPage() {
    const router = useRouter();
    const {
        register,
        handleSubmit,
        formState: { errors }
    } = useForm<LoginFormInputs>();

    const onSubmit: SubmitHandler<LoginFormInputs> = async (data) => {
        try {
            // /api/auth/login 호출 (axios baseURL이 /api라고 가정 시 /auth/login)
            // 백엔드 Controller가 /api/auth/login 이므로 baseURL이 /api라면 /auth/login이 맞음
            const response = await api.post('/auth/login', {
                username: data.email, // 백엔드 LoginRequest가 username 필드를 사용함
                password: data.password
            });

            const { accessToken, refreshToken } = response.data;

            // 토큰 저장
            localStorage.setItem('accessToken', accessToken);
            localStorage.setItem('refreshToken', refreshToken);

            // 헤더 설정 (다음 요청부터 적용)
            api.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;

            // 메인 페이지 또는 대시보드로 이동
            router.push('/');
        } catch (error) {
            // 에러 처리는 Axios Interceptor가 토스트를 띄워줌
            console.error("Login Failed", error);
        }
    };

    return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4">
            <div className="w-full max-w-md space-y-8 rounded-2xl bg-white p-8 shadow-xl ring-1 ring-gray-900/5">
                <div className="text-center">
                    <h2 className="text-3xl font-bold tracking-tight text-gray-900">
                        로그인
                    </h2>
                    <p className="mt-2 text-sm text-gray-600">
                        서비스 이용을 위해 로그인해주세요.
                    </p>
                </div>

                <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
                    <div className="space-y-4">
                        <div>
                            <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                                아이디
                            </label>
                            <div className="mt-1">
                                <input
                                    id="email"
                                    placeholder="Username"
                                    {...register("email", {
                                        required: "아이디를 입력해주세요.",
                                        minLength: {
                                            value: 4,
                                            message: "아이디는 4자 이상이어야 합니다."
                                        }
                                    })}
                                    className={`
                                        block w-full rounded-md border-0 py-2.5 px-3 shadow-sm ring-1 ring-inset sm:text-sm sm:leading-6 transition-all outline-none
                                        ${errors.email
                                            ? 'ring-red-500 text-red-900 focus:ring-2 focus:ring-red-500 placeholder:text-red-300'
                                            : 'ring-gray-300 text-gray-900 focus:ring-2 focus:ring-indigo-600'
                                        }
                                    `}
                                />
                                {errors.email && (
                                    <p className="mt-1 text-sm text-red-600">
                                        {errors.email.message}
                                    </p>
                                )}
                            </div>
                        </div>

                        <div>
                            <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                                비밀번호
                            </label>
                            <div className="mt-1">
                                <input
                                    id="password"
                                    type="password"
                                    autoComplete="current-password"
                                    {...register("password", {
                                        required: "비밀번호를 입력해주세요."
                                    })}
                                    className={`
                                        block w-full rounded-md border-0 py-2.5 px-3 shadow-sm ring-1 ring-inset sm:text-sm sm:leading-6 transition-all outline-none
                                        ${errors.password
                                            ? 'ring-red-500 text-red-900 focus:ring-2 focus:ring-red-500 placeholder:text-red-300'
                                            : 'ring-gray-300 text-gray-900 focus:ring-2 focus:ring-indigo-600'
                                        }
                                    `}
                                />
                                {errors.password && (
                                    <p className="mt-1 text-sm text-red-600">
                                        {errors.password.message}
                                    </p>
                                )}
                            </div>
                        </div>
                    </div>

                    <div>
                        <button
                            type="submit"
                            className="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 transition-colors"
                        >
                            로그인
                        </button>
                    </div>
                </form>

                <div className="text-center text-sm text-gray-500">
                    계정이 없으신가요?{' '}
                    <a href="#" className="font-semibold text-indigo-600 hover:text-indigo-500">
                        회원가입
                    </a>
                </div>
            </div>
        </div>
    );
}
