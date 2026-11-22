/**
 * 桌面“数字生命”前后端通信客户端（严格对齐 docs/api.md）
 * 支持同源托管，也支持通过 window.__DIGITALLIFE_API_BASE__ 指定后端地址。
 */
(function (global) {
  'use strict';

  const API_BASE = (global.__DIGITALLIFE_API_BASE__ || '').replace(/\/+$/, '');

  class ApiError extends Error {
    constructor(message, code, status) {
      super(message || '请求失败');
      this.name = 'ApiError';
      this.code = code;
      this.status = status;
    }
  }

  async function requestJson(path, options = {}) {
    const headers = Object.assign(
      { Accept: 'application/json' },
      options.body ? { 'Content-Type': 'application/json' } : {},
      options.headers || {}
    );

    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers
    });

    let envelope = null;
    try {
      envelope = await res.json();
    } catch (_) {
      envelope = null;
    }

    if (!res.ok) {
      const msg = (envelope && envelope.message) || `请求失败 (HTTP ${res.status})`;
      const code = (envelope && envelope.code) || res.status;
      throw new ApiError(msg, code, res.status);
    }

    if (envelope && typeof envelope.code === 'number' && envelope.code !== 0) {
      throw new ApiError(envelope.message || '服务端返回异常', envelope.code, res.status);
    }

    return envelope ? envelope.data : null;
  }

  function sendChatMessage(content) {
    return requestJson('/api/v1/chat/send', {
      method: 'POST',
      body: JSON.stringify({ content })
    });
  }

  function fetchChatHistory({ beforeId = null, limit = 20 } = {}) {
    const params = new URLSearchParams();
    if (beforeId !== null && beforeId !== undefined) {
      params.set('beforeId', String(beforeId));
    }
    params.set('limit', String(limit));
    return requestJson(`/api/v1/chat/history?${params.toString()}`, {
      method: 'GET'
    });
  }

  function fetchConfig() {
    return requestJson('/api/v1/config', { method: 'GET' });
  }

  function updateConfig(payload) {
    return requestJson('/api/v1/config', {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  }

  function fetchSensors() {
    return requestJson('/api/v1/sensors', { method: 'GET' });
  }

  function toggleSensor(name, enabled) {
    return requestJson(`/api/v1/sensors/${encodeURIComponent(name)}`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled: Boolean(enabled) })
    });
  }

  function resolveWsUrl() {
    if (API_BASE) {
      const url = new URL(API_BASE);
      const proto = url.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${proto}//${url.host}/ws/events`;
    }
    const proto = global.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${global.location.host}/ws/events`;
  }

  function createEventSocket({ onOpen, onClose, onEvent } = {}) {
    let ws = null;
    let closedByUser = false;
    let retryCount = 0;
    let reconnectTimer = null;

    function connect() {
      if (closedByUser) return;
      const url = resolveWsUrl();
      try {
        ws = new WebSocket(url);
      } catch (_) {
        scheduleReconnect();
        return;
      }

      ws.onopen = () => {
        const isReconnect = retryCount > 0;
        retryCount = 0;
        if (typeof onOpen === 'function') {
          onOpen({ isReconnect });
        }
      };

      ws.onclose = () => {
        if (typeof onClose === 'function') {
          onClose();
        }
        if (!closedByUser) {
          scheduleReconnect();
        }
      };

      ws.onerror = () => {
        // 交由 onclose 统一触发重连
      };

      ws.onmessage = (ev) => {
        let parsed = null;
        try {
          parsed = JSON.parse(ev.data);
        } catch (_) {
          return;
        }
        if (parsed && typeof onEvent === 'function') {
          onEvent(parsed);
        }
      };
    }

    function scheduleReconnect() {
      if (reconnectTimer) clearTimeout(reconnectTimer);
      retryCount += 1;
      const delay = retryCount === 1 ? 1500 : retryCount === 2 ? 3000 : 5000;
      reconnectTimer = setTimeout(connect, delay);
    }

    connect();

    return {
      close() {
        closedByUser = true;
        if (reconnectTimer) clearTimeout(reconnectTimer);
        if (ws) ws.close();
      }
    };
  }

  global.DigitalLifeApi = {
    ApiError,
    sendChatMessage,
    fetchChatHistory,
    fetchConfig,
    updateConfig,
    fetchSensors,
    toggleSensor,
    createEventSocket
  };
})(window);
