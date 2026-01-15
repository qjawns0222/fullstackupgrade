import { useEffect } from 'react';
import { dispatchToast } from '@/lib/toast-event';

export const useNotification = () => {
    useEffect(() => {
        const token = localStorage.getItem('accessToken');
        const url = token
            ? `http://localhost:8080/api/notifications/subscribe?token=${token}`
            : 'http://localhost:8080/api/notifications/subscribe';

        const eventSource = new EventSource(url);

        eventSource.onopen = () => {
            console.log('SSE Connected');
        };

        eventSource.onmessage = (event) => {
            // Default message handler
            console.log('SSE Message:', event.data);
        };

        eventSource.addEventListener('analysis-complete', (event) => {
            console.log('Analysis Complete Event:', event.data);
            dispatchToast(event.data, 'success');
        });

        eventSource.onerror = (error) => {
            console.error('SSE Error:', error);
            eventSource.close();
            // Optional: Implement reconnection logic here
        };

        return () => {
            console.log('SSE Disconnected');
            eventSource.close();
        };
    }, []);
};
