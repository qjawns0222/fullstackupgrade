'use client';

import React, { useState } from 'react';
import api from '@/lib/axios';
import { QRCodeSVG } from 'qrcode.react';
import { useForm } from 'react-hook-form';

export default function MfaSetupPage() {
    const [qrUri, setQrUri] = useState<string | null>(null);
    const [secret, setSecret] = useState<string | null>(null);
    const [setupStarted, setSetupStarted] = useState(false);
    const { register, handleSubmit, formState: { errors } } = useForm<{ otp: string }>();

    const startSetup = async () => {
        try {
            const res = await api.post('/mfa/setup');
            setQrUri(res.data.qrCodeUri);
            setSecret(res.data.secret);
            setSetupStarted(true);
        } catch (e) {
            console.error(e);
            alert("MFA 설정을 시작할 수 없습니다.");
        }
    };

    const onVerify = async (data: { otp: string }) => {
        try {
            const res = await api.post('/mfa/enable', {
                username: "", // Backend uses @AuthenticationPrincipal
                otp: data.otp
            });
            if (res.data === true) {
                alert("2단계 인증이 활성화되었습니다!");
                setQrUri(null);
                setSecret(null);
                setSetupStarted(false);
            }
        } catch (e) {
            console.error(e);
            alert("인증에 실패했습니다. 코드를 다시 확인해주세요.");
        }
    };

    return (
        <div className="p-8 max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold mb-6">2단계 인증 설정</h1>

            <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
                {!setupStarted ? (
                    <div className="text-center">
                        <p className="mb-4 text-gray-600">
                            보안을 강화하려면 2단계 인증을 설정하세요. Google Authenticator 앱이 필요합니다.
                        </p>
                        <button
                            onClick={startSetup}
                            className="bg-indigo-600 text-white px-4 py-2 rounded-md hover:bg-indigo-700 transition"
                        >
                            설정 시작하기
                        </button>
                    </div>
                ) : (
                    <div className="space-y-6">
                        <div className="text-center">
                            <p className="mb-4 text-sm font-medium text-gray-700">1. 아래 QR 코드를 앱으로 스캔하세요.</p>
                            {qrUri && (
                                <div className="flex justify-center p-4 bg-white border rounded">
                                    <QRCodeSVG value={qrUri} size={200} />
                                </div>
                            )}
                            <p className="mt-2 text-xs text-gray-400">Secret: {secret}</p>
                        </div>

                        <div className="border-t pt-6">
                            <p className="mb-4 text-sm font-medium text-gray-700">2. 앱에 표시된 6자리 코드를 입력하세요.</p>
                            <form onSubmit={handleSubmit(onVerify)} className="flex gap-4">
                                <input
                                    type="text"
                                    maxLength={6}
                                    placeholder="000000"
                                    {...register("otp", { required: true, pattern: /^[0-9]{6}$/ })}
                                    className="block w-full rounded-md border-0 py-1.5 px-3 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                                />
                                <button
                                    type="submit"
                                    className="bg-indigo-600 text-white px-4 py-2 rounded-md hover:bg-indigo-700 whitespace-nowrap"
                                >
                                    활성화
                                </button>
                            </form>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
