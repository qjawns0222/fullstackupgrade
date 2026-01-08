"use client";

import React, { useEffect, useState } from 'react';
import { Toast } from './Toast';
import { TOAST_EVENT_NAME } from '@/lib/toast-event';
import { ToastEventDetail } from '@/types/ToastEventDetail';

export const ToastContainer = () => {
    const [toasts, setToasts] = useState<ToastEventDetail[]>([]);

    useEffect(() => {
        const handleToastEvent = (event: CustomEvent<ToastEventDetail>) => {
            const newToast = event.detail;
            setToasts((prev) => [...prev, newToast]);
        };

        if (typeof window !== 'undefined') {
            window.addEventListener(TOAST_EVENT_NAME, handleToastEvent as EventListener);
        }

        return () => {
            if (typeof window !== 'undefined') {
                window.removeEventListener(TOAST_EVENT_NAME, handleToastEvent as EventListener);
            }
        };
    }, []);

    const removeToast = (id: string) => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
    };

    return (
        <div className="fixed top-4 right-4 z-50 flex flex-col space-y-4 w-full max-w-xs pointer-events-none">
            <div className="pointer-events-auto flex flex-col gap-2">
                {toasts.map((toast) => (
                    <Toast
                        key={toast.id}
                        id={toast.id}
                        message={toast.message}
                        type={toast.type}
                        onClose={removeToast}
                    />
                ))}
            </div>
        </div>
    );
};
