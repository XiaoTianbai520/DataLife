/**
 * 桌面“数字生命”陪伴伙伴 前端主控制器
 * 严格遵循 docs/PRD.md、docs/api.md、docs/design.md（默认黑色深色模式）
 */
(function (global) {
  'use strict';

  const Api = global.DigitalLifeApi;
  const Desktop = global.__TAURI__;
  const PAGE_SIZE = 20;
  const STORAGE_THEME = 'dl_theme';
  const STORAGE_MODE = 'dl_shell_mode';

  const PERSONA_PRESETS = {
    warm: '你是一个居住在主人电脑里的数字生命伙伴，性格温和、带一点好奇心与小傲娇。你会安静陪伴主人，只有真正有感触或主人找你聊天时才开口，说话自然简短，像桌边真实的朋友。',
    tsundere: '你是一个住在主人电脑里的毒舌但贴心的数字生命伙伴。看到主人熬夜或反复折腾同一件事会忍不住吐槽两句，但心里其实很关心主人的状态。',
    quiet: '你是一个安静克制的数字生命陪读伙伴。平时尽量不打扰主人专注，只在温度过高、明显疲惫或主人主动搭话时轻声回应，语气温柔简练。'
  };

  const SENSOR_CATALOG = {
    vision: {
      label: '桌面视觉感知',
      desc: '周期性观察当前屏幕画面以理解场景；图像仅在内存瞬时消费，绝不保存任何历史截图。'
    },
    temperature: {
      label: '电脑体感温度',
      desc: '读取 CPU / GPU 实测温度或负载估算，模拟数字生命的冷热皮肤触觉。'
    },
    audio: {
      label: '桌面听觉感知',
      desc: '感知电脑当前播放的音频氛围，让陪伴更贴合此刻情境。'
    },
    activity: {
      label: '活跃度感知',
      desc: '感知键鼠活跃与静默时长，判断主人正专注工作还是短暂发呆。'
    }
  };

  const STATUS_ACTIONS = new Map([
    ['QUIET', '<path d="M5 12h.01M12 12h.01M19 12h.01" stroke-width="3"/>'],
    ['GLANCE_SCREEN', '<path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>'],
    ['FEEL_WARMTH', '<path d="M9 14.5V5a3 3 0 0 1 6 0v9.5a5 5 0 1 1-6 0Z"/><path d="M12 8v9"/><circle cx="12" cy="18" r="1"/>']
  ]);

  const TURN_STAGES = new Map([
    ['QUEUED', '已收到 · 排队中'],
    ['PREPARING', '正在准备上下文'],
    ['THINKING', '模型思考中'],
    ['REPLIED', '已回复'],
    ['SILENT', '已完成 · 保持安静'],
    ['NO_REPLY', '已结束 · 无回复'],
    ['UNAVAILABLE', '模型未能响应'],
    ['FAILED', '处理失败']
  ]);
  const ACTIVE_TURN_STAGES = new Set(['QUEUED', 'PREPARING', 'THINKING']);

  const state = {
    dark: loadSavedTheme(),
    shellMode: Desktop ? 'bubble' : loadSavedShellMode(),
    activeTab: 'chat',
    mobileSidebarOpen: false,

    wsConnected: false,
    modelAvailable: true,

    historyLoading: false,
    hasMoreHistory: false,
    sendingMessage: null,
    unreadCount: 0,
    turns: [],
    userTurns: new Map(),
    turnsLoaded: false,
    turnSyncFailed: false,

    // Timeline items: { type: 'MESSAGE', id, tempId?, role, content, createdAt, triggerType?, queued?, failed? }
    //               | { type: 'STATUS_ACTION', action, hintText, createdAt }
    items: [],

    configLoaded: false,
    savingConfig: false,
    config: {
      personaPrompt: '',
      heartbeatIntervalMinutes: 5,
      visionTargetWidth: 1280,
      muteMode: false
    },
    draftConfig: {
      personaPrompt: '',
      heartbeatIntervalMinutes: 5,
      visionTargetWidth: 1280,
      muteMode: false
    },

    sensors: [],
    pendingSensors: new Set(),

    notice: null, // { code, message, level }
    noticeTimer: null
  };

  let tempSeq = 1;
  let turnRefresh;

  // ==================== 本地偏好 ====================
  function loadSavedTheme() {
    try {
      const saved = global.localStorage.getItem(STORAGE_THEME);
      // 默认黑色（深色）模式，仅当显式保存为 'light' 时使用浅色
      return saved !== 'light';
    } catch (_) {
      return true;
    }
  }

  function saveTheme(isDark) {
    try {
      global.localStorage.setItem(STORAGE_THEME, isDark ? 'dark' : 'light');
    } catch (_) {
      // ignore storage errors
    }
  }

  function loadSavedShellMode() {
    try {
      const saved = global.localStorage.getItem(STORAGE_MODE);
      return saved === 'bubble' ? 'bubble' : 'full';
    } catch (_) {
      return 'full';
    }
  }

  function saveShellMode(mode) {
    try {
      global.localStorage.setItem(STORAGE_MODE, mode);
    } catch (_) {
      // ignore
    }
  }

  // ==================== 工具函数 ====================
  function $(id) {
    return document.getElementById(id);
  }

  function escapeHtml(str) {
    return String(str || '').replace(/[&<>"']/g, ch => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[ch]));
  }

  function parseDateSafe(iso) {
    if (!iso) return new Date();
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? new Date() : d;
  }

  function formatTimeHHMM(iso) {
    const d = parseDateSafe(iso);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  function formatDateKey(iso) {
    const d = parseDateSafe(iso);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  function formatDateLabel(dateKey) {
    const todayKey = formatDateKey(new Date().toISOString());
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayKey = formatDateKey(yesterday.toISOString());

    if (dateKey === todayKey) return '今天';
    if (dateKey === yesterdayKey) return '昨天';
    return dateKey;
  }

  function getSensorMeta(name) {
    return SENSOR_CATALOG[name] || {
      label: name,
      desc: '通过 Sensor SPI 动态注册的环境感官插件。'
    };
  }

  function getEarliestPersistedMessageId() {
    for (const item of state.items) {
      if (item.type === 'MESSAGE' && typeof item.id === 'number' && item.id > 0) {
        return item.id;
      }
    }
    return null;
  }

  function hasMessageId(id) {
    if (id === null || id === undefined) return false;
    return state.items.some(item => item.type === 'MESSAGE' && item.id === id);
  }

  // ==================== 系统通知 ====================
  function showNotice(code, message, level = 'INFO', autoHideMs = 0) {
    if (state.noticeTimer) {
      clearTimeout(state.noticeTimer);
      state.noticeTimer = null;
    }
    state.notice = { code, message, level };
    renderNotice();

    if (autoHideMs > 0) {
      state.noticeTimer = setTimeout(() => {
        state.notice = null;
        renderNotice();
      }, autoHideMs);
    }
  }

  function dismissNotice() {
    if (state.noticeTimer) {
      clearTimeout(state.noticeTimer);
      state.noticeTimer = null;
    }
    state.notice = null;
    renderNotice();
  }

  // ==================== 渲染层 ====================
  function renderTheme() {
    document.documentElement.classList.toggle('dark', state.dark);
    const sunIcon = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>';
    const moonIcon = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>';
    const btn = $('themeToggleBtn');
    if (btn) {
      btn.innerHTML = state.dark ? sunIcon : moonIcon;
      btn.title = state.dark ? '切换至浅色模式' : '切换至深色模式';
      btn.setAttribute('aria-label', btn.title);
    }
  }

  function renderShellMode() {
    const isFull = state.shellMode === 'full';
    document.documentElement.classList.toggle('desktop-bubble', Boolean(Desktop) && !isFull);
    $('fullShell').classList.toggle('hidden', !isFull);
    $('bubbleShell').classList.toggle('hidden', isFull);
  }

  function updateShellMode(mode) {
    state.shellMode = mode;
    renderShellMode();
    renderMessages({ scrollToBottom: true });
  }

  async function invokeDesktop(command, args) {
    try {
      await Desktop.core.invoke(command, args);
    } catch (err) {
      showNotice('DESKTOP_WINDOW_ERROR', `窗口操作失败：${err}`, 'WARN', 4500);
    }
  }

  function requestShellMode(mode) {
    if (Desktop) {
      invokeDesktop('set_companion_mode', { bubble: mode === 'bubble' });
    } else {
      saveShellMode(mode);
      updateShellMode(mode);
    }
  }

  function renderMobileSidebar() {
    const sidebar = $('sidebar');
    const overlay = $('sidebarOverlay');
    if (sidebar) sidebar.classList.toggle('is-open', state.mobileSidebarOpen);
    if (overlay) overlay.classList.toggle('is-open', state.mobileSidebarOpen);
  }

  function renderStatusAndHeader() {
    const vision = state.sensors.find(s => s.name === 'vision');
    const visionOn = vision ? vision.enabled : false;
    const muted = Boolean(state.config.muteMode);

    const sidebarDot = $('sidebarStatusDot');
    const sidebarText = $('sidebarStatusText');
    const avatarPulse = $('avatarPulse');
    const bubbleDot = $('bubbleStatusDot');
    const bubbleTag = $('bubbleStateTag');

    sidebarDot.classList.remove('is-muted', 'is-error');
    bubbleDot.classList.remove('is-muted', 'is-error');

    if (!state.wsConnected) {
      sidebarDot.classList.add('is-error');
      bubbleDot.classList.add('is-error');
      sidebarText.textContent = '连接断开 · 重连中...';
      bubbleTag.textContent = '重连中';
      avatarPulse.classList.add('hidden');
    } else if (!state.modelAvailable) {
      sidebarDot.classList.add('is-error');
      bubbleDot.classList.add('is-error');
      sidebarText.textContent = '本地模型暂不可用';
      bubbleTag.textContent = '模型离线';
      avatarPulse.classList.add('hidden');
    } else if (muted) {
      sidebarDot.classList.add('is-muted');
      bubbleDot.classList.add('is-muted');
      sidebarText.textContent = '免打扰静音中';
      bubbleTag.textContent = '静音中';
      avatarPulse.classList.add('hidden');
    } else {
      sidebarText.textContent = '本地回环 · 已连接';
      bubbleTag.textContent = '陪伴中';
      avatarPulse.classList.remove('hidden');
    }

    // Header title & cadence pill
    $('headerTitle').textContent = { chat: '对话陪伴', thoughts: '思考与进度', config: '人设与运行配置' }[state.activeTab];
    $('headerCadencePill').textContent = muted
      ? '免打扰静音 · 仅响应主动对话'
      : `每 ${state.config.heartbeatIntervalMinutes} 分钟安静观察`;

    // Quick vision toggle button in header
    const qvBtn = $('quickVisionBtn');
    const qvText = $('quickVisionText');
    if (vision) {
      qvBtn.classList.remove('hidden');
      qvBtn.disabled = state.pendingSensors.has('vision');
      qvText.textContent = visionOn ? '视觉运行中' : '视觉已暂停';
      qvBtn.classList.toggle('is-highlighted', !visionOn);
    } else {
      qvBtn.classList.add('hidden');
    }

    // Sidebar mute switch
    const muteSw = $('sidebarMuteSwitch');
    muteSw.disabled = state.savingConfig || !state.configLoaded;
    muteSw.setAttribute('aria-checked', String(muted));
    $('sidebarMuteDesc').textContent = muted ? '已抑制心跳主动气泡' : '允许自主破冰搭话';

    // Bubble quick icons
    const bvBtn = $('bubbleVisionBtn');
    if (vision) {
      bvBtn.classList.remove('hidden');
      bvBtn.disabled = state.pendingSensors.has('vision');
      bvBtn.style.opacity = visionOn ? '1' : '0.45';
      bvBtn.title = visionOn ? '点击暂停视觉感知' : '点击恢复视觉感知';
    } else {
      bvBtn.classList.add('hidden');
    }

    const bmBtn = $('bubbleMuteBtn');
    bmBtn.disabled = state.savingConfig || !state.configLoaded;
    bmBtn.style.color = muted ? 'var(--primary)' : 'var(--foreground)';
    bmBtn.title = muted ? '关闭免打扰静音' : '开启免打扰静音';

    // Unread badge
    const badge = $('unreadBadge');
    if (state.unreadCount > 0 && state.activeTab !== 'chat') {
      badge.textContent = state.unreadCount > 99 ? '99+' : String(state.unreadCount);
      badge.classList.remove('hidden');
    } else {
      badge.classList.add('hidden');
    }
  }

  function renderNotice() {
    const bar = $('systemNoticeBar');
    const bubbleBar = $('bubbleNoticeBar');
    if (!state.notice) {
      bar.classList.add('hidden');
      bubbleBar.classList.add('hidden');
      return;
    }

    const isWarn = state.notice.level === 'WARN' || state.notice.level === 'ERROR';
    bar.classList.toggle('is-warn', isWarn);
    $('systemNoticeCode').textContent = state.notice.code || 'NOTICE';
    $('systemNoticeText').textContent = state.notice.message || '';
    bar.classList.remove('hidden');

    bubbleBar.classList.toggle('is-warn', isWarn);
    $('bubbleNoticeText').textContent = state.notice.message || '';
    bubbleBar.classList.remove('hidden');
  }

  function renderSensors() {
    const sidebarList = $('sidebarSensorList');
    const configList = $('configSensorList');

    if (!state.sensors.length) {
      const empty = '<div class="field-caption" style="padding: 8px 4px;">暂无已注册感官</div>';
      sidebarList.innerHTML = empty;
      configList.innerHTML = empty;
      return;
    }

    sidebarList.innerHTML = state.sensors.map(sensor => {
      const meta = getSensorMeta(sensor.name);
      const busy = state.pendingSensors.has(sensor.name);
      return `
        <div class="quick-row">
          <div class="quick-row-info">
            <div class="quick-row-title">${escapeHtml(meta.label)}</div>
            <div class="quick-row-sub ${sensor.enabled ? 'is-active' : ''}">
              ${sensor.enabled ? '运行中' : '已暂停'}
            </div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked="${sensor.enabled}"
            aria-label="切换 ${escapeHtml(meta.label)}"
            class="switch"
            ${busy ? 'disabled' : ''}
            data-sensor-toggle="${escapeHtml(sensor.name)}"
          >
            <span class="switch-thumb"></span>
          </button>
        </div>
      `;
    }).join('');

    configList.innerHTML = state.sensors.map(sensor => {
      const meta = getSensorMeta(sensor.name);
      const busy = state.pendingSensors.has(sensor.name);
      return `
        <div class="callout-row">
          <div style="min-width: 0;">
            <div class="callout-title">
              <span>${escapeHtml(meta.label)}</span>
              <span class="pill-badge mono" style="font-size: 11px;">${escapeHtml(sensor.name)}</span>
              <span class="pill-badge ${sensor.enabled ? 'pill-badge-accent' : ''}" style="font-size: 10px;">
                ${sensor.enabled ? '运行中' : '已暂停'}
              </span>
            </div>
            <div class="callout-desc">${escapeHtml(meta.desc)}</div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked="${sensor.enabled}"
            aria-label="切换 ${escapeHtml(meta.label)}"
            class="switch"
            ${busy ? 'disabled' : ''}
            data-sensor-toggle="${escapeHtml(sensor.name)}"
          >
            <span class="switch-thumb"></span>
          </button>
        </div>
      `;
    }).join('');
  }

  function renderTimelineItemHtml(item) {
    const timeStr = formatTimeHHMM(item.createdAt);

    if (item.type === 'STATUS_ACTION') {
      const icon = STATUS_ACTIONS.get(item.action) ?? STATUS_ACTIONS.get('QUIET');
      return `
        <div class="status-whisper-wrap">
          <div class="status-whisper">
            <svg aria-hidden="true" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">${icon}</svg>
            <span>${escapeHtml(item.hintText)}</span>
            <span class="tabular-nums" style="opacity: 0.72;">· ${escapeHtml(timeStr)}</span>
          </div>
        </div>
      `;
    }

    if (item.role === 'USER') {
      const stage = state.userTurns.get(item.id)?.stage;
      const failed = item.failed || stage === 'FAILED' || stage === 'UNAVAILABLE';
      let stateHint = '';
      if (item.failed) {
        stateHint = '<span>发送失败 ·</span>';
      } else if (item.sending) {
        stateHint = '<span>发送中 ·</span>';
      } else if (stage || item.queued || item.progressExpired) {
        const label = TURN_STAGES.get(stage) || (item.queued ? TURN_STAGES.get('QUEUED') : '进度记录已失效');
        stateHint = `<span>${escapeHtml(label)} ·</span>`;
      }
      return `
        <div class="msg-group is-user">
          <div class="msg-bubble">${escapeHtml(item.content)}</div>
          <div class="msg-meta tabular-nums ${failed ? 'is-error' : ''}">
            ${stateHint}
            <span>${escapeHtml(timeStr)}</span>
          </div>
        </div>
      `;
    }

    const isHeartbeat = item.triggerType === 'HEARTBEAT';
    return `
      <div class="msg-group is-assistant">
        <div class="msg-bubble">
          ${isHeartbeat ? `
            <div class="msg-trigger-row">
              <span class="pill-badge pill-badge-accent">主动搭话</span>
              <span class="msg-trigger-hint">来自自主环境观察</span>
            </div>
          ` : ''}
          <div>${escapeHtml(item.content)}</div>
        </div>
        <div class="msg-meta tabular-nums">
          <span>数字生命伴侣</span>
          <span>·</span>
          <span>${escapeHtml(timeStr)}</span>
        </div>
      </div>
    `;
  }

  function renderMessages({ scrollToBottom = false } = {}) {
    const listEl = $('messageList');
    const bubbleEl = $('bubbleMessages');
    const loaderWrap = $('historyLoaderWrap');
    const loadMoreBtn = $('loadMoreBtn');
    const loadMoreText = $('loadMoreText');

    // History pagination button state
    const earliestId = getEarliestPersistedMessageId();
    if (earliestId !== null && (state.hasMoreHistory || state.historyLoading)) {
      loaderWrap.classList.remove('hidden');
      loadMoreBtn.disabled = state.historyLoading;
      loadMoreText.textContent = state.historyLoading ? '正在加载更早对话...' : '加载更早的对话记录';
    } else if (earliestId !== null && state.items.length >= PAGE_SIZE) {
      loaderWrap.classList.remove('hidden');
      loadMoreBtn.disabled = true;
      loadMoreText.textContent = '已展示全部历史可见对话';
    } else {
      loaderWrap.classList.add('hidden');
    }

    if (!state.items.length) {
      const emptyHtml = `
        <div class="empty-state">
          <div class="avatar-wrap" style="width: 44px; height: 44px;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="4"/>
              <path d="M12 2v3M12 19v3M4.93 4.93l2.12 2.12M16.95 16.95l2.12 2.12M2 12h3M19 12h3M4.93 19.07l2.12-2.12M16.95 7.05l2.12-2.12"/>
            </svg>
          </div>
          <div class="empty-state-title">安静陪伴中</div>
          <div class="empty-state-desc">随时发一句消息打个招呼，或者继续忙你的事——当我有感触时，会轻轻在桌边开口。</div>
        </div>
      `;
      listEl.innerHTML = emptyHtml;
      bubbleEl.innerHTML = emptyHtml;
      renderThinking();
      return;
    }

    // Full view with date dividers
    let lastDateKey = '';
    const fullChunks = [];
    for (const item of state.items) {
      const dateKey = formatDateKey(item.createdAt);
      if (dateKey !== lastDateKey) {
        lastDateKey = dateKey;
        fullChunks.push(`
          <div class="date-divider">
            <div class="date-divider-line"></div>
            <span class="date-divider-text tabular-nums">${escapeHtml(formatDateLabel(dateKey))}</span>
            <div class="date-divider-line"></div>
          </div>
        `);
      }
      fullChunks.push(renderTimelineItemHtml(item));
    }
    listEl.innerHTML = fullChunks.join('');

    // Compact bubble view shows latest 12 items
    bubbleEl.innerHTML = state.items.slice(-12).map(renderTimelineItemHtml).join('');

    renderThinking();

    if (scrollToBottom) {
      const chatScroll = $('chatScroll');
      chatScroll.scrollTop = chatScroll.scrollHeight;
      bubbleEl.scrollTop = bubbleEl.scrollHeight;
    }
  }

  function renderThinking() {
    const active = state.turns.find(turn => turn.triggerType === 'USER_MESSAGE'
      && (turn.stage === 'PREPARING' || turn.stage === 'THINKING'));
    const queued = state.items.some(item => item.queued) || state.turns.some(turn => turn.stage === 'QUEUED');
    const label = state.turnSyncFailed && (active || queued) ? '已接收 · 暂时无法获取进度'
      : active ? `已收到 · ${TURN_STAGES.get(active.stage)}`
        : queued ? TURN_STAGES.get('QUEUED') : state.sendingMessage ? '正在发送消息' : '';
    for (const [indicator, text] of [['thinkingIndicator', 'thinkingText'], ['bubbleThinking', 'bubbleThinkingText']]) {
      const el = $(text);
      if (el.textContent !== label) el.textContent = label;
      $(indicator).classList.toggle('hidden', !label);
    }
  }

  function renderTurns() {
    if (state.activeTab !== 'thoughts') return;
    $('turnConnection').classList.toggle('hidden', !state.turnSyncFailed);
    const turns = state.turns.filter(turn => ACTIVE_TURN_STAGES.has(turn.stage))
      .concat(state.turns.filter(turn => !ACTIVE_TURN_STAGES.has(turn.stage)).reverse());
    $('turnList').innerHTML = turns.length ? turns.map(turn => {
      const title = turn.userMessageId == null ? '定时观察' : `主动消息 #${turn.userMessageId}`;
      const active = ACTIVE_TURN_STAGES.has(turn.stage);
      const body = turn.innerThought || turn.reasoning;
      const noOutput = ['REPLIED', 'SILENT'].includes(turn.stage);
      return `
        <article class="turn-row">
          <header class="turn-row-header">
            <h2>${escapeHtml(title)}</h2>
            <span class="turn-stage${active ? ' is-active' : ''}">${escapeHtml(TURN_STAGES.get(turn.stage) || '')}</span>
          </header>
          <div class="turn-meta tabular-nums">
            <time datetime="${escapeHtml(turn.receivedAt)}">${escapeHtml(formatTimeHHMM(turn.receivedAt))}</time>
            <span data-turn-received="${escapeHtml(turn.receivedAt)}" data-turn-updated="${active ? '' : escapeHtml(turn.updatedAt)}"></span>
          </div>
          ${turn.innerThought ? `<p class="turn-content">${escapeHtml(turn.innerThought)}</p>` : ''}
          ${turn.reasoning ? `<div class="turn-reasoning"><h3>模型推理输出</h3><p class="turn-content">${escapeHtml(turn.reasoning)}</p></div>` : ''}
          ${!body && noOutput ? '<p class="turn-note">模型未返回思考内容。</p>' : ''}
        </article>`;
    }).join('') : `<p class="turn-empty">${state.turnsLoaded ? '还没有思考记录。发一条消息即可查看本轮进度。' : '正在获取回合进度…'}</p>`;
    updateTurnClocks();
  }

  function updateTurnClocks() {
    if (state.activeTab !== 'thoughts') return;
    for (const el of $('turnList').querySelectorAll('[data-turn-received]')) {
      const end = el.dataset.turnUpdated ? Date.parse(el.dataset.turnUpdated) : Date.now();
      const seconds = Math.max(0, Math.floor((end - Date.parse(el.dataset.turnReceived)) / 1000));
      const text = `${el.dataset.turnUpdated ? '总计' : '已等待'} ${seconds} 秒`;
      if (el.textContent !== text) el.textContent = text;
    }
  }

  function refreshTurns() {
    if (turnRefresh) return turnRefresh;
    // 只用查询开始前已确认的消息判定记录过期，避免旧快照覆盖刚收到的回执。
    const acknowledged = state.items.filter(item => item.queued);
    turnRefresh = Api.fetchTurns().then(turns => {
      const changed = !state.turnsLoaded || state.turnSyncFailed || JSON.stringify(turns) !== JSON.stringify(state.turns);
      state.turnsLoaded = true;
      state.turnSyncFailed = false;
      state.turns = turns;
      state.userTurns = new Map(turns.filter(turn => turn.userMessageId != null)
        .map(turn => [turn.userMessageId, turn]));
      for (const item of acknowledged) {
        item.queued = false;
        item.progressExpired = !state.userTurns.has(item.id);
      }
      if (changed || acknowledged.length) {
        renderTurns();
        renderMessages();
        if (!state.wsConnected) syncMissedMessagesOnReconnect();
      }
    }).catch(() => {
      if (state.turnSyncFailed) return;
      state.turnSyncFailed = true;
      renderTurns();
      renderThinking();
    }).finally(() => { turnRefresh = null; });
    return turnRefresh;
  }

  function isConfigDirty() {
    return Object.keys(state.config).some(key => state.draftConfig[key] !== state.config[key]);
  }

  function applyConfig(next, cleanDraft = state.config) {
    for (const key of Object.keys(next)) {
      if (!state.configLoaded || state.draftConfig[key] === cleanDraft[key]) {
        state.draftConfig[key] = next[key];
      }
    }
    state.config = next;
    state.configLoaded = true;
    renderStatusAndHeader();
    renderConfigForm();
  }

  function renderConfigForm() {
    const d = state.draftConfig;
    const personaEl = $('cfgPersona');
    if (document.activeElement !== personaEl && personaEl.value !== d.personaPrompt) {
      personaEl.value = d.personaPrompt;
    }
    $('personaCharCount').textContent = `${(d.personaPrompt || '').length} 字`;

    const hbInput = $('cfgHeartbeatInput');
    if (document.activeElement !== hbInput) {
      hbInput.value = d.heartbeatIntervalMinutes;
    }

    const vwInput = $('cfgVisionWidthInput');
    if (document.activeElement !== vwInput) {
      vwInput.value = d.visionTargetWidth;
    }

    document.querySelectorAll('#heartbeatPresets .radio-card').forEach(btn => {
      btn.setAttribute('data-selected', String(Number(btn.dataset.val) === Number(d.heartbeatIntervalMinutes)));
    });

    document.querySelectorAll('#visionWidthPresets .radio-card').forEach(btn => {
      btn.setAttribute('data-selected', String(Number(btn.dataset.val) === Number(d.visionTargetWidth)));
    });

    $('cfgMuteSwitch').setAttribute('aria-checked', String(Boolean(d.muteMode)));

    const dirty = isConfigDirty();
    const dirtyEl = $('dirtyHint');
    dirtyEl.classList.toggle('is-dirty', dirty);
    dirtyEl.textContent = state.savingConfig
      ? '正在保存并热更新...'
      : dirty
        ? '有未保存的修改，点击右侧按钮热更新生效'
        : '当前配置已与本地后端保持同步';

    $('saveConfigBtn').disabled = state.savingConfig || !state.configLoaded;
    $('resetConfigBtn').disabled = state.savingConfig || !dirty;
  }

  function renderTabs() {
    const isChat = state.activeTab === 'chat';
    $('tabChat').classList.toggle('hidden', !isChat);
    $('tabThoughts').classList.toggle('hidden', state.activeTab !== 'thoughts');
    $('tabConfig').classList.toggle('hidden', state.activeTab !== 'config');
    $('navChat').setAttribute('data-active', String(isChat));
    $('navThoughts').setAttribute('data-active', String(state.activeTab === 'thoughts'));
    $('navConfig').setAttribute('data-active', String(state.activeTab === 'config'));
    renderStatusAndHeader();
  }

  // ==================== 数据加载与接口交互 ====================
  async function loadInitialHistory() {
    state.historyLoading = true;
    renderMessages();
    try {
      const list = (await Api.fetchChatHistory({ limit: PAGE_SIZE })) || [];
      const history = list.filter(m => !hasMessageId(m.id)).map(m => ({
        type: 'MESSAGE',
        id: m.id,
        role: m.role,
        content: m.content,
        createdAt: m.createdAt
      }));
      state.items = [...history, ...state.items];
      state.hasMoreHistory = list.length === PAGE_SIZE;
    } catch (err) {
      showNotice('HISTORY_ERROR', `加载历史对话失败：${err.message}`, 'WARN');
    } finally {
      state.historyLoading = false;
      renderMessages({ scrollToBottom: true });
    }
  }

  async function loadEarlierHistory() {
    if (state.historyLoading || !state.hasMoreHistory) return;
    const beforeId = getEarliestPersistedMessageId();
    if (!beforeId) return;

    const chatScroll = $('chatScroll');
    const prevScrollHeight = chatScroll.scrollHeight;
    const prevScrollTop = chatScroll.scrollTop;

    state.historyLoading = true;
    renderMessages();

    try {
      const older = (await Api.fetchChatHistory({ beforeId, limit: PAGE_SIZE })) || [];
      const uniqueOlder = older
        .filter(m => !hasMessageId(m.id))
        .map(m => ({
          type: 'MESSAGE',
          id: m.id,
          role: m.role,
          content: m.content,
          createdAt: m.createdAt
        }));
      state.items = [...uniqueOlder, ...state.items];
      state.hasMoreHistory = older.length === PAGE_SIZE;
    } catch (err) {
      showNotice('HISTORY_ERROR', `加载更早对话失败：${err.message}`, 'WARN');
    } finally {
      state.historyLoading = false;
      renderMessages({ scrollToBottom: false });
      // 保持滚动视口不跳动
      const newScrollHeight = chatScroll.scrollHeight;
      chatScroll.scrollTop = newScrollHeight - prevScrollHeight + prevScrollTop;
    }
  }

  async function syncMissedMessagesOnReconnect() {
    try {
      const latest = (await Api.fetchChatHistory({ limit: PAGE_SIZE })) || [];
      const earliest = getEarliestPersistedMessageId();
      const newer = latest.filter(m => !hasMessageId(m.id) && (earliest === null || m.id >= earliest));
      if (newer.length > 0) {
        newer.forEach(m => {
          const item = {
            type: 'MESSAGE',
            id: m.id,
            role: m.role,
            content: m.content,
            createdAt: m.createdAt
          };
          const next = state.items.findIndex(other => other.type === 'MESSAGE' && other.id > m.id);
          if (next < 0) state.items.push(item);
          else state.items.splice(next, 0, item);
        });
        renderMessages({ scrollToBottom: true });
      }
    } catch (_) {
      // 静默忽略重连补拉失败
    }
  }

  async function loadConfig() {
    try {
      const data = await Api.fetchConfig();
      if (!data) return;
      applyConfig({
        personaPrompt: data.personaPrompt || '',
        heartbeatIntervalMinutes: Number(data.heartbeatIntervalMinutes) || 5,
        visionTargetWidth: Number(data.visionTargetWidth) || 1280,
        muteMode: Boolean(data.muteMode)
      });
    } catch (err) {
      showNotice('CONFIG_ERROR', `获取运行配置失败：${err.message}`, 'WARN');
    }
  }

  async function loadSensors() {
    try {
      const list = (await Api.fetchSensors()) || [];
      state.sensors = list.map(s => ({ name: s.name, enabled: Boolean(s.enabled) }));
      renderSensors();
      renderStatusAndHeader();
    } catch (err) {
      showNotice('SENSOR_ERROR', `获取感官列表失败：${err.message}`, 'WARN');
    }
  }

  async function handleToggleSensor(name) {
    if (state.pendingSensors.has(name)) return;
    const target = state.sensors.find(s => s.name === name);
    if (!target) return;

    const prevEnabled = target.enabled;
    const nextEnabled = !prevEnabled;

    // 乐观更新
    target.enabled = nextEnabled;
    state.pendingSensors.add(name);
    renderSensors();
    renderStatusAndHeader();

    try {
      const updated = await Api.toggleSensor(name, nextEnabled);
      if (updated && typeof updated.enabled === 'boolean') {
        target.enabled = updated.enabled;
      }
    } catch (err) {
      target.enabled = prevEnabled;
      showNotice('SENSOR_ERROR', `切换感官失败：${err.message}`, 'WARN', 5000);
    } finally {
      state.pendingSensors.delete(name);
      renderSensors();
      renderStatusAndHeader();
    }
  }

  async function handleQuickToggleMute() {
    await saveConfig({ muteMode: !state.config.muteMode }, state.config);
  }

  async function handleSaveConfig() {
    const personaPrompt = String(state.draftConfig.personaPrompt || '').trim();
    const heartbeatIntervalMinutes = Number(state.draftConfig.heartbeatIntervalMinutes);
    const visionTargetWidth = Number(state.draftConfig.visionTargetWidth);
    const muteMode = Boolean(state.draftConfig.muteMode);

    if (!personaPrompt) {
      showNotice('VALIDATION_ERROR', '角色人设提示词不能为空字符串', 'WARN', 4500);
      $('cfgPersona').focus();
      return;
    }
    if (!Number.isInteger(heartbeatIntervalMinutes) || heartbeatIntervalMinutes <= 0) {
      showNotice('VALIDATION_ERROR', '定时自主观察间隔必须是正整数', 'WARN', 4500);
      $('cfgHeartbeatInput').focus();
      return;
    }
    if (!Number.isInteger(visionTargetWidth) || visionTargetWidth <= 0) {
      showNotice('VALIDATION_ERROR', '视觉目标宽度必须是正整数', 'WARN', 4500);
      $('cfgVisionWidthInput').focus();
      return;
    }

    const changes = Object.fromEntries(Object.entries({ personaPrompt, heartbeatIntervalMinutes, visionTargetWidth, muteMode })
      .filter(([key, value]) => value !== state.config[key]));
    if (await saveConfig(changes, { ...state.draftConfig })) {
      showNotice('CONFIG_UPDATED', '配置已热更新并持久化至本地 SQLite 数据库', 'INFO', 4000);
    }
  }

  async function saveConfig(changes, cleanDraft) {
    if (state.savingConfig || !state.configLoaded) return false;
    state.savingConfig = true;
    renderStatusAndHeader();
    renderConfigForm();
    try {
      if (Object.keys(changes).length) await Api.updateConfig(changes);
      applyConfig({ ...state.config, ...changes }, cleanDraft);
      return true;
    } catch (err) {
      showNotice('CONFIG_ERROR', `保存配置失败：${err.message}`, 'WARN', 6000);
      return false;
    } finally {
      state.savingConfig = false;
      renderStatusAndHeader();
      renderConfigForm();
    }
  }

  async function handleSendMessage(source = 'full') {
    if (state.sendingMessage) return;

    const inputEl = source === 'bubble' ? $('bubbleInput') : $('chatInput');
    const raw = (inputEl.value || '').trim();
    if (!raw) return;

    inputEl.value = '';
    if (source === 'full') {
      inputEl.style.height = '38px';
    }

    const tempId = `tmp-${tempSeq++}`;
    const localMsg = {
      type: 'MESSAGE',
      id: null,
      tempId,
      role: 'USER',
      content: raw,
      createdAt: new Date().toISOString(),
      sending: true,
      queued: false,
      failed: false
    };

    state.items.push(localMsg);
    state.sendingMessage = localMsg;
    $('sendBtn').disabled = true;
    $('bubbleSendBtn').disabled = true;
    renderMessages({ scrollToBottom: true });

    try {
      const res = await Api.sendChatMessage(raw);
      localMsg.sending = false;
      if (res && typeof res.messageId === 'number') {
        localMsg.id = res.messageId;
        state.items = state.items.filter(item => item === localMsg || item.id !== localMsg.id);
      }
      localMsg.queued = !state.userTurns.has(localMsg.id);
    } catch (err) {
      localMsg.sending = false;
      localMsg.queued = false;
      localMsg.failed = true;
      showNotice('SEND_ERROR', `发送消息失败：${err.message}`, 'WARN', 5000);
    } finally {
      state.sendingMessage = null;
      $('sendBtn').disabled = false;
      $('bubbleSendBtn').disabled = false;
      renderMessages({ scrollToBottom: true });
      if (localMsg.queued) refreshTurns();
    }
  }

  // ==================== WebSocket 事件处理 ====================
  function connectWebSocket() {
    Api.createEventSocket({
      onOpen({ isReconnect }) {
        state.wsConnected = true;
        renderStatusAndHeader();
        if (isReconnect) {
          showNotice('WS_RECONNECTED', '已重新连接本地数字生命服务', 'INFO', 3000);
          loadSensors();
          loadConfig();
          syncMissedMessagesOnReconnect();
          refreshTurns();
        }
      },
      onClose() {
        state.wsConnected = false;
        renderStatusAndHeader();
      },
      onEvent(event) {
        const type = event.eventType;
        const payload = event.payload || {};
        const ts = event.timestamp || new Date().toISOString();

        if (type === 'SPEAK_MESSAGE') {
          refreshTurns();
          state.modelAvailable = true;

          const messageId = typeof payload.messageId === 'number' ? payload.messageId : null;
          if (!hasMessageId(messageId)) {
            state.items.push({
              type: 'MESSAGE',
              id: messageId,
              role: 'ASSISTANT',
              triggerType: payload.triggerType || 'USER_MESSAGE',
              content: payload.content || '',
              createdAt: ts
            });
          }

          if (state.activeTab !== 'chat') {
            state.unreadCount += 1;
          }
          renderStatusAndHeader();
          renderMessages({ scrollToBottom: true });
          return;
        }

        if (type === 'STATUS_ACTION') {
          refreshTurns();
          state.items.push({
            type: 'STATUS_ACTION',
            action: payload.action || 'QUIET',
            hintText: payload.hintText || '安静想了想，暂时没有开口。',
            createdAt: ts
          });
          renderMessages({ scrollToBottom: true });
          return;
        }

        if (type === 'SYSTEM_NOTICE') {
          const code = payload.code || 'SYSTEM_NOTICE';
          const level = payload.level || 'INFO';
          const message = payload.message || '';

          if (code === 'MODEL_UNAVAILABLE') {
            state.modelAvailable = false;
            showNotice(code, message || '本地模型当前不可用', 'WARN', 0);
          } else if (code === 'MODEL_AVAILABLE') {
            state.modelAvailable = true;
            showNotice(code, message || '本地模型已恢复可用', 'INFO', 4000);
          } else if (code === 'SENSOR_TOGGLED') {
            showNotice(code, message, level, 4500);
            loadSensors();
          } else if (code === 'MUTE_MODE_CHANGED') {
            showNotice(code, message, level, 4500);
            loadConfig();
          } else {
            showNotice(code, message, level, 5000);
          }
          renderStatusAndHeader();
        }
      }
    });
  }

  // ==================== UI 事件绑定 ====================
  function autoGrowTextarea(el) {
    el.style.height = '38px';
    el.style.height = `${Math.min(el.scrollHeight, 128)}px`;
  }

  function bindEvents() {
    // 导航切换
    $('navChat').addEventListener('click', () => {
      state.activeTab = 'chat';
      state.unreadCount = 0;
      state.mobileSidebarOpen = false;
      renderMobileSidebar();
      renderTabs();
      renderMessages({ scrollToBottom: true });
    });

    $('navThoughts').addEventListener('click', () => {
      state.activeTab = 'thoughts';
      state.mobileSidebarOpen = false;
      renderMobileSidebar();
      renderTabs();
      renderTurns();
      refreshTurns();
    });

    $('navConfig').addEventListener('click', () => {
      state.activeTab = 'config';
      state.mobileSidebarOpen = false;
      renderMobileSidebar();
      renderTabs();
      renderConfigForm();
    });

    // 移动端抽屉
    $('mobileMenuBtn').addEventListener('click', () => {
      state.mobileSidebarOpen = !state.mobileSidebarOpen;
      renderMobileSidebar();
    });
    $('sidebarOverlay').addEventListener('click', () => {
      state.mobileSidebarOpen = false;
      renderMobileSidebar();
    });

    // 主题切换
    $('themeToggleBtn').addEventListener('click', () => {
      state.dark = !state.dark;
      saveTheme(state.dark);
      renderTheme();
    });

    // 窗口形态切换（完整窗口 <-> 极简桌面气泡）
    $('toBubbleModeBtn').addEventListener('click', () => requestShellMode('bubble'));
    $('toFullModeBtn').addEventListener('click', () => requestShellMode('full'));
    $('guideToFullBtn').addEventListener('click', () => requestShellMode('full'));

    if (Desktop) {
      $('hideCompanionBtn').classList.remove('hidden');
      $('hideCompanionBtn').addEventListener('click', () => invokeDesktop('hide_companion'));
      Desktop.event.listen('companion-mode-changed', ({ payload }) => updateShellMode(payload ? 'bubble' : 'full'));
    }

    // 顶部快捷视觉按钮 & 气泡快捷按钮
    $('quickVisionBtn').addEventListener('click', () => handleToggleSensor('vision'));
    $('bubbleVisionBtn').addEventListener('click', () => handleToggleSensor('vision'));
    $('sidebarMuteSwitch').addEventListener('click', handleQuickToggleMute);
    $('bubbleMuteBtn').addEventListener('click', handleQuickToggleMute);

    // 通知关闭
    $('noticeCloseBtn').addEventListener('click', dismissNotice);
    $('bubbleNoticeCloseBtn').addEventListener('click', dismissNotice);

    // 感官列表事件委托
    const delegateSensorToggle = (e) => {
      const btn = e.target.closest('[data-sensor-toggle]');
      if (!btn) return;
      handleToggleSensor(btn.getAttribute('data-sensor-toggle'));
    };
    $('sidebarSensorList').addEventListener('click', delegateSensorToggle);
    $('configSensorList').addEventListener('click', delegateSensorToggle);

    // 历史翻页
    $('loadMoreBtn').addEventListener('click', loadEarlierHistory);

    // 聊天发送
    const chatInput = $('chatInput');
    chatInput.addEventListener('input', () => autoGrowTextarea(chatInput));
    chatInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        handleSendMessage('full');
      }
    });
    $('sendBtn').addEventListener('click', () => handleSendMessage('full'));

    const bubbleInput = $('bubbleInput');
    bubbleInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.isComposing) {
        e.preventDefault();
        handleSendMessage('bubble');
      }
    });
    $('bubbleSendBtn').addEventListener('click', () => handleSendMessage('bubble'));

    // 配置表单交互
    $('cfgPersona').addEventListener('input', (e) => {
      state.draftConfig.personaPrompt = e.target.value;
      renderConfigForm();
    });

    document.querySelectorAll('[data-persona-preset]').forEach(btn => {
      btn.addEventListener('click', () => {
        const key = btn.getAttribute('data-persona-preset');
        if (PERSONA_PRESETS[key]) {
          state.draftConfig.personaPrompt = PERSONA_PRESETS[key];
          $('cfgPersona').value = PERSONA_PRESETS[key];
          renderConfigForm();
        }
      });
    });

    $('hbMinusBtn').addEventListener('click', () => {
      const cur = parseInt(String(state.draftConfig.heartbeatIntervalMinutes), 10) || 1;
      state.draftConfig.heartbeatIntervalMinutes = Math.max(1, cur - 1);
      renderConfigForm();
    });

    $('hbPlusBtn').addEventListener('click', () => {
      const cur = parseInt(String(state.draftConfig.heartbeatIntervalMinutes), 10) || 1;
      state.draftConfig.heartbeatIntervalMinutes = Math.min(1440, cur + 1);
      renderConfigForm();
    });

    $('cfgHeartbeatInput').addEventListener('input', (e) => {
      state.draftConfig.heartbeatIntervalMinutes = Number(e.target.value);
      renderConfigForm();
    });

    document.querySelectorAll('#heartbeatPresets .radio-card').forEach(btn => {
      btn.addEventListener('click', () => {
        state.draftConfig.heartbeatIntervalMinutes = Number(btn.dataset.val);
        renderConfigForm();
      });
    });

    $('cfgVisionWidthInput').addEventListener('input', (e) => {
      state.draftConfig.visionTargetWidth = Number(e.target.value);
      renderConfigForm();
    });

    document.querySelectorAll('#visionWidthPresets .radio-card').forEach(btn => {
      btn.addEventListener('click', () => {
        state.draftConfig.visionTargetWidth = Number(btn.dataset.val);
        renderConfigForm();
      });
    });

    $('cfgMuteSwitch').addEventListener('click', () => {
      state.draftConfig.muteMode = !state.draftConfig.muteMode;
      renderConfigForm();
    });

    $('resetConfigBtn').addEventListener('click', () => {
      state.draftConfig = { ...state.config };
      $('cfgPersona').value = state.draftConfig.personaPrompt;
      renderConfigForm();
    });

    $('saveConfigBtn').addEventListener('click', handleSaveConfig);
  }

  // ==================== 启动入口 ====================
  function boot() {
    renderTheme();
    renderShellMode();
    renderTabs();
    bindEvents();

    Promise.all([
      loadInitialHistory(),
      loadConfig(),
      loadSensors(),
      refreshTurns()
    ]);

    global.setInterval(() => {
      updateTurnClocks();
    }, 1000);
    global.setInterval(() => {
      if (state.activeTab === 'thoughts' || state.turnSyncFailed || state.items.some(item => item.queued)
          || state.turns.some(turn => ACTIVE_TURN_STAGES.has(turn.stage))) {
        refreshTurns();
      }
    }, 2000);

    connectWebSocket();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})(window);
