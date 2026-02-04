import { useEffect } from 'react';
import { dispatchToast } from '@/lib/toast-event';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

export const useNotification = () => {
    useEffect(() => {
        // Use Gateway URL (port 8000)
        const socket = new SockJS('http://localhost:8000/ws');

        const client = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 5000,
            onConnect: () => {
                console.log('Connected to WebSocket');
                client.subscribe('/topic/notifications', (message) => {
                    try {
                        const body = JSON.parse(message.body);
                        console.log('Received:', body);
                        // Access message content. NotificationMessage has 'message' field
                        dispatchToast(body.message, 'success');
                    } catch (e) {
                        console.error("Failed to parse message", e);
                        dispatchToast("Received notification", 'success');
                    }
                });
            },
            onStompError: (frame) => {
                console.error('Broker reported error: ' + frame.headers['message']);
                console.error('Additional details: ' + frame.body);
            },
        });

        client.activate();

        return () => {
            client.deactivate();
        };
    }, []);
};
