"use client";

import React, { useEffect } from 'react';

import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { ToastProps } from '@/types/ToastProps';
import { ToastType } from '@/types/ToastType';

function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}


const icons: Record<ToastType, React.ReactNode> = {
    success: (
        <svg className="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7"></path>
        </svg>
    ),
    error: (
        <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
    ),
    warning: (
        <svg className="w-5 h-5 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
        </svg>
    ),
    info: (
        <svg className="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
        </svg>
    ),
};

export const Toast: React.FC<ToastProps> = ({ id, message, type, onClose }) => {
    useEffect(() => {
        const timer = setTimeout(() => {
            onClose(id);
        }, 3000); // 3초 후 자동 삭제

        return () => clearTimeout(timer);
    }, [id, onClose]);

    return (
        <div
            className={cn(
                "flex items-center w-full max-w-xs p-4 space-x-4 ml-auto",
                "bg-white/90 backdrop-blur-md border border-gray-100 rounded-xl shadow-lg",
                "transition-all duration-300 ease-in-out transform translate-x-0 hover:scale-[1.02]",
                "animate-in slide-in-from-right-full fade-in duration-300",
                type === 'error' && "border-l-4 border-l-red-500",
                type === 'success' && "border-l-4 border-l-green-500",
                type === 'warning' && "border-l-4 border-l-yellow-500",
                type === 'info' && "border-l-4 border-l-blue-500"
            )}
            role="alert"
        >
            <div className="flex-shrink-0">{icons[type]}</div>
            <div className="flex-1 text-sm font-medium text-gray-800 break-words">{message}</div>
            <button
                onClick={() => onClose(id)}
                className="flex-shrink-0 text-gray-400 hover:text-gray-900 transition-colors"
            >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
            </button>
        </div>
    );
};
