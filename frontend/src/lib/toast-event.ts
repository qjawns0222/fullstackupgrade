import { ToastEventDetail } from "@/types/ToastEventDetail";
import { ToastType } from "@/types/ToastType";


export const TOAST_EVENT_NAME = 'app:toast';

export const dispatchToast = (message: string, type: ToastType = 'info') => {
    if (typeof window !== 'undefined') {
        const event = new CustomEvent<ToastEventDetail>(TOAST_EVENT_NAME, {
            detail: {
                id: Math.random().toString(36).substring(2, 9),
                message,
                type
            },
        });
        window.dispatchEvent(event);
    }
};

