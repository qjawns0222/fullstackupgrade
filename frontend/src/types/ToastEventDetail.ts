import { ToastType } from "./ToastType";

export interface ToastEventDetail {
    id: string;
    message: string;
    type: ToastType;
}