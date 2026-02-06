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

    const [mfaRequired, setMfaRequired] = React.useState(false);
    const [loginUsername, setLoginUsername] = React.useState('');
    const { register: mfaRegister, handleSubmit: mfaHandleSubmit, formState: { errors: mfaErrors } } = useForm<{ otp: string }>();

    const onSubmit: SubmitHandler<LoginFormInputs> = async (data) => {
        try {
            const response = await api.post('/auth/login', {
                username: data.email,
                password: data.password
            });

            if (response.data.mfaRequired) {
                setMfaRequired(true);
                setLoginUsername(data.email);
                return;
            }

            const { accessToken, refreshToken } = response.data;
            handleLoginSuccess(accessToken, refreshToken);

        } catch (error) {
            console.error("Login Failed", error);
        }
    };

    const onMfaSubmit = async (data: { otp: string }) => {
        try {
            const response = await api.post('/mfa/verify-login', {
                username: loginUsername,
                otp: data.otp
            });

            const { accessToken, refreshToken } = response.data;
            handleLoginSuccess(accessToken, refreshToken);
        } catch (error) {
            console.error("MFA Validation Failed", error);
            alert("OTP 인증에 실패했습니다.");
        }
    };

    const handleLoginSuccess = (accessToken: string, refreshToken: string) => {
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', refreshToken);
        api.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
        router.push('/');
    };

    if (mfaRequired) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4">
                <div className="w-full max-w-md space-y-8 rounded-2xl bg-white p-8 shadow-xl ring-1 ring-gray-900/5">
                    <div className="text-center">
                        <h2 className="text-3xl font-bold tracking-tight text-gray-900">2단계 인증</h2>
                        <p className="mt-2 text-sm text-gray-600">Google Authenticator 앱의 6자리 코드를 입력하세요.</p>
                    </div>
                    <form className="mt-8 space-y-6" onSubmit={mfaHandleSubmit(onMfaSubmit)}>
                        <div>
                            <label htmlFor="otp" className="block text-sm font-medium text-gray-700">인증 코드</label>
                            <div className="mt-1">
                                <input
                                    id="otp"
                                    type="text"
                                    maxLength={6}
                                    {...mfaRegister("otp", { required: "코드를 입력해주세요", pattern: { value: /^[0-9]{6}$/, message: "6자리 숫자여야 합니다" } })}
                                    className="block w-full rounded-md border-0 py-2.5 px-3 shadow-sm ring-1 ring-inset sm:text-sm sm:leading-6 ring-gray-300 focus:ring-2 focus:ring-indigo-600"
                                />
                                {mfaErrors.otp && (
                                    <p className="mt-1 text-sm text-red-600">{mfaErrors.otp.message}</p>
                                )}
                            </div>
                        </div>
                        <button
                            type="submit"
                            className="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 transition-colors"
                        >
                            인증하기
                        </button>
                    </form>
                </div>
            </div>
        );
    }

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
