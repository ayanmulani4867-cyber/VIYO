type EventHandler = (data: any) => void;

class WebSocketClient {
  private ws: WebSocket | null = null;
  private listeners: Map<string, Set<EventHandler>> = new Map();
  private reconnectTimer: number | null = null;
  private token: string | null = null;

  connect(token: string) {
    this.token = token;
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    let url = import.meta.env.VITE_WS_URL;
    if (url) {
      url = `${url}${url.includes('?') ? '&' : '?'}token=${token}`;
    } else {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.port === '5173' ? '127.0.0.1:8000' : window.location.host;
      url = `${protocol}//${host}/api/v1/ws?token=${token}`;
    }

    try {
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        console.log('[VIYO WS] Connected to signaling gateway.');
      };

      this.ws.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const eventType = payload.event;
          if (eventType && this.listeners.has(eventType)) {
            this.listeners.get(eventType)?.forEach((handler) => handler(payload));
          }
        } catch (err) {
          console.error('[VIYO WS] Parse error:', err);
        }
      };

      this.ws.onclose = () => {
        console.log('[VIYO WS] Connection closed. Reconnecting in 3s...');
        this.scheduleReconnect();
      };

      this.ws.onerror = (error) => {
        console.warn('[VIYO WS] WebSocket error:', error);
      };
    } catch (e) {
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = window.setTimeout(() => {
      if (this.token) this.connect(this.token);
    }, 3000);
  }

  on(event: string, handler: EventHandler) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)?.add(handler);
    return () => {
      this.listeners.get(event)?.delete(handler);
    };
  }

  send(data: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}

export const wsClient = new WebSocketClient();

