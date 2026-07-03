/* ═══════════════════════════════════════════════════════════════════
   LogStream UI — app.js
   Architecture (top-to-bottom dependency order):
     Config · Auth · API · Store · FilterEngine · Utils
     Renderer · StatsBar · CounterBar · Poller
     StatusIndicator · Toast · App (bootstrap)
   ═══════════════════════════════════════════════════════════════════ */
'use strict';

// ──────────────────────────────────────────────────────────────────
// CONFIG
// ──────────────────────────────────────────────────────────────────
const Config = Object.freeze({
  apiLogs:      '/logstream/api/logs',
  apiStats:     '/logstream/api/stats',
  apiLogin:     '/logstream/login',
  apiLogout:    '/logstream/logout',
  pollMs:       2000,      // polling interval
  maxDomCards:  1500,      // prune DOM after this many cards
  maxStore:     4000,      // prune store after this many entries
  genHeader:    'x-logstream-generation',
});

// ──────────────────────────────────────────────────────────────────
// AUTH
// ──────────────────────────────────────────────────────────────────
/**
 * Auth module — handles the full authentication lifecycle.
 *
 * Responsibilities:
 *  1. On startup: probe the API to determine auth status (200 vs 401).
 *  2. If 200: hide loading screen, show dashboard (security disabled or
 *     session still valid from a previous load).
 *  3. If 401: show login page.
 *  4. On login form submit: POST credentials, handle success/failure.
 *  5. On logout: POST to logout endpoint, return to login page.
 *  6. Intercept any 401 that arrives during polling and redirect to login.
 *
 * Design principles:
 *  - Never stores credentials anywhere (no localStorage, sessionStorage, cookies).
 *  - Relies entirely on the HttpOnly LOGSTREAM_SESSION cookie managed by the browser.
 *  - All fetch calls include `credentials: 'include'` so the session cookie is
 *    sent automatically by the browser.
 *  - Security-disabled mode (backend always returns 200) never shows login UI.
 */
const Auth = {
  /**
   * Authentication state machine.
   *   'loading'         — startup probe in progress
   *   'unauthenticated' — 401 on probe or expired session; login page visible
   *   'authenticated'   — valid session or security disabled; dashboard visible
   *
   * onUnauthorized() only redirects to login when state === 'authenticated'.
   * This prevents the redirect loop: poller fires a 401 immediately after
   * login before the session cookie is fully committed by the browser.
   */
  _state: 'loading',

  /**
   * Prevents double-submit: true while a login fetch is in-flight.
   * Cleared in the finally block of _doLogin().
   */
  _isAuthenticating: false,

  /** Whether security is active on the backend. Determined at startup. */
  securityEnabled: false,

  // ── DOM refs ────────────────────────────────────────────────────
  _loadingEl:  null,
  _loginPage:  null,
  _loginForm:  null,
  _usernameEl: null,
  _passwordEl: null,
  _submitBtn:  null,
  _errorEl:    null,
  _errorMsgEl: null,
  _logoutBtn:  null,

  // ── Lifecycle ───────────────────────────────────────────────────

  /**
   * Must be called before App.init().
   * Probes the logs API to determine authentication status, then either
   * shows the dashboard or the login page.
   *
   * @returns {Promise<void>} resolves when the UI is in the correct state
   */
  async bootstrap() {
    this._state = 'loading';
    this._cacheRefs();
    this._bindLoginForm();
    this._bindLogoutBtn();

    try {
      const res = await fetch(Config.apiLogs, {
        credentials: 'include',
        headers: { 'Accept': 'application/json' },
      });

      if (res.ok) {
        // Security disabled OR valid session — go straight to dashboard.
        this.securityEnabled = false;
        this._state = 'authenticated';
        this._showDashboard();
      } else if (res.status === 401) {
        // Security is enabled, user is not authenticated.
        this.securityEnabled = true;
        this._state = 'unauthenticated';
        this._showLogin();
      } else {
        // Unexpected status — show dashboard; poller will handle retries.
        this._state = 'authenticated';
        this._showDashboard();
      }
    } catch (_networkErr) {
      // Network failure — show dashboard; poller status indicator will show ERROR.
      this._state = 'authenticated';
      this._showDashboard();
    }
  },

  /**
   * Called by API.fetchLogs / API.clear when they receive an unexpected 401
   * mid-session (e.g. session expired while the tab was open).
   * Stops the poller and redirects to the login page.
   */
  onUnauthorized() {
    // BUG FIX: Only redirect when actually on the dashboard.
    // Without this guard, a 401 from the poller's very first tick
    // (fired immediately after Poller.start() was called from _doLogin())
    // would redirect the user back to the login page even though login
    // just succeeded — creating the "immediately returns to login" loop.
    if (this._state !== 'authenticated') return;
    this._state = 'unauthenticated';
    Poller.pause();
    Store.clear();
    Renderer.clear();
    StatsBar.refresh();
    CounterBar.set(0, 0);
    this._showLogin();
  },

  // ── UI transitions ──────────────────────────────────────────────

  _showDashboard() {
    this._hide(this._loadingEl);
    // Use classList directly so the .visible CSS class is removed regardless
    // of whether the element has the .hidden class or not.
    this._loginPage.classList.remove('visible');
    if (this.securityEnabled) {
      this._show(this._logoutBtn);
    }
  },

  _showLogin() {
    this._hide(this._loadingEl);
    this._loginPage.classList.add('visible');
    this._hide(this._logoutBtn);
    // Autofocus username field (delay so CSS transition doesn't fight focus)
    setTimeout(() => this._usernameEl?.focus(), 120);
  },

  _showLoading() {
    this._loadingEl.style.display = 'flex';
    this._loginPage.classList.remove('visible');
  },

  // ── Login form ──────────────────────────────────────────────────

  _bindLoginForm() {
    // Primary handler: form submit event (Enter key in any field, or button click
    // propagated to the form). e.preventDefault() stops native page navigation.
    this._loginForm?.addEventListener('submit', e => {
      e.preventDefault();
      e.stopPropagation();
      if (!this._isAuthenticating) this._doLogin();
    });

    // BUG FIX: Also bind directly to the button click event.
    //
    // When the submit button has keyboard focus and the user presses Space,
    // the browser fires 'click' on the button. The document keydown handler
    // (now fixed separately) used to intercept Space and call Poller.toggle()
    // instead. Binding here as a belt-and-suspenders safety net ensures the
    // button is always responsive even if keyboard handler quirks recur.
    //
    // We call e.preventDefault() on the click to prevent the browser from
    // additionally bubbling a 'submit' event (avoiding double invocation).
    // The _isAuthenticating flag is the definitive dedup guard.
    this._submitBtn?.addEventListener('click', e => {
      e.preventDefault();
      if (!this._isAuthenticating) this._doLogin();
    });
  },

  async _doLogin() {
    // BUG FIX: Hard guard against concurrent submissions.
    // _bindLoginForm() checks this flag before calling here, but this guard
    // covers any scenario where _doLogin() might be called directly.
    if (this._isAuthenticating) return;

    const username = this._usernameEl?.value.trim() ?? '';
    const password = this._passwordEl?.value ?? '';

    if (!username || !password) {
      this._showError('Please enter both username and password.');
      return;
    }

    this._isAuthenticating = true;
    this._setLoading(true);
    this._hideError();

    try {
      const res = await fetch(Config.apiLogin, {
        method:      'POST',
        credentials: 'include',
        headers:     { 'Content-Type': 'application/json' },
        body:        JSON.stringify({ username, password }),
      });

      if (res.ok) {
        // Clear the password field immediately — never keep it in the DOM.
        this._passwordEl.value = '';
        this.securityEnabled = true;
        // IMPORTANT: set _state to 'authenticated' BEFORE showing the dashboard
        // and starting the poller. This arms onUnauthorized() so it will respond
        // to a genuine future 401 (expired session), while the guard inside
        // onUnauthorized() prevents a spurious redirect caused by the poller's
        // first tick returning 401 in a race condition.
        this._state = 'authenticated';
        this._showDashboard();
        // Start polling. The first tick is deferred by pollMs (see Poller.start)
        // to eliminate the race condition where the cookie from the login
        // response hadn't reached the browser cookie store before the next fetch.
        Poller.start();
      } else {
        let msg = 'Invalid username or password.';
        try {
          const body = await res.json();
          if (body && body.message) msg = body.message;
          else if (body && body.error) msg = body.error;
        } catch (_) { /* response wasn't JSON — use default message */ }
        this._showError(msg);
        this._passwordEl.value = '';
        this._passwordEl.focus();
      }
    } catch (_networkErr) {
      this._showError('Cannot reach the server. Check your connection.');
    } finally {
      this._isAuthenticating = false;
      this._setLoading(false);
    }
  },

  // ── Logout ──────────────────────────────────────────────────────

  _bindLogoutBtn() {
    this._logoutBtn?.addEventListener('click', () => this._doLogout());
  },

  async _doLogout() {
    this._state = 'unauthenticated'; // prevent onUnauthorized from double-firing
    Poller.pause();
    try {
      await fetch(Config.apiLogout, {
        method:      'POST',
        credentials: 'include',
      });
    } catch (_) { /* best-effort */ }

    Store.clear();
    Renderer.clear();
    StatsBar.refresh();
    CounterBar.set(0, 0);
    this._showLogin();
  },

  // ── Error banner ────────────────────────────────────────────────

  _showError(msg) {
    if (this._errorMsgEl) this._errorMsgEl.textContent = msg;
    this._errorEl?.classList.add('visible');
    this._usernameEl?.classList.add('input-error');
    this._passwordEl?.classList.add('input-error');
  },

  _hideError() {
    this._errorEl?.classList.remove('visible');
    this._usernameEl?.classList.remove('input-error');
    this._passwordEl?.classList.remove('input-error');
  },

  // ── Submit loading state ─────────────────────────────────────────

  _setLoading(on) {
    if (!this._submitBtn) return;
    this._submitBtn.disabled = on;
    this._submitBtn.classList.toggle('loading', on);
    if (this._usernameEl) this._usernameEl.disabled = on;
    if (this._passwordEl) this._passwordEl.disabled = on;
  },

  // ── DOM helpers ─────────────────────────────────────────────────

  _cacheRefs() {
    this._loadingEl  = document.getElementById('auth-loading');
    this._loginPage  = document.getElementById('login-page');
    this._loginForm  = document.getElementById('login-form');
    this._usernameEl = document.getElementById('login-username');
    this._passwordEl = document.getElementById('login-password');
    this._submitBtn  = document.getElementById('login-submit');
    this._errorEl    = document.getElementById('login-error');
    this._errorMsgEl = document.getElementById('login-error-msg');
    this._logoutBtn  = document.getElementById('btn-logout');
  },

  _show(el) { el?.classList.remove('hidden'); },
  _hide(el) { el?.classList.add('hidden'); },
};

// ──────────────────────────────────────────────────────────────────
// API
// ──────────────────────────────────────────────────────────────────
const API = {
  /**
   * Fetch log entries.
   * Pass afterId=-1 for all, or last known id for delta.
   * Always sends credentials so the session cookie is included.
   * Calls Auth.onUnauthorized() on 401 — the poller will stop itself.
   */
  async fetchLogs(afterId = -1) {
    const url = afterId >= 0
      ? `${Config.apiLogs}?afterId=${afterId}`
      : Config.apiLogs;
    const res = await fetch(url, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' },
    });
    if (res.status === 401) {
      Auth.onUnauthorized();
      throw new Error('HTTP 401 — redirecting to login');
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const gen  = res.headers.get(Config.genHeader);
    const data = await res.json();
    return { entries: data, generation: gen };
  },

  /** Clear the server-side log buffer. */
  async clear() {
    const res = await fetch(Config.apiLogs, {
      method:      'DELETE',
      credentials: 'include',
    });
    if (res.status === 401) {
      Auth.onUnauthorized();
    }
  },
};

// ──────────────────────────────────────────────────────────────────
// STORE
// ──────────────────────────────────────────────────────────────────
const Store = {
  entries:    [],          // LogEntry[]
  seenIds:    new Set(),   // prevents duplicates across restarts
  counts:     { INFO: 0, WARN: 0, ERROR: 0, DEBUG: 0, TRACE: 0 },
  generation: null,        // X-LogStream-Generation header value

  /**
   * Returns entries from `incoming` that we have not seen before.
   * Handles server restarts transparently via seenIds.
   */
  absorb(incoming, gen) {
    const isRestart = this.generation !== null && gen !== null && gen !== this.generation;
    if (isRestart) this._reset();
    if (gen) this.generation = gen;

    const novel = incoming.filter(e => !this.seenIds.has(e.id));
    for (const e of novel) {
      this.seenIds.add(e.id);
      this.entries.push(e);
      if (this.counts[e.level] !== undefined) this.counts[e.level]++;
    }
    // Prune store
    if (this.entries.length > Config.maxStore) {
      const removed = this.entries.splice(0, this.entries.length - Config.maxStore);
      for (const r of removed) this.seenIds.delete(r.id);
    }
    return novel;
  },

  _reset() {
    this.entries  = [];
    this.seenIds  = new Set();
    this.counts   = { INFO: 0, WARN: 0, ERROR: 0, DEBUG: 0, TRACE: 0 };
    this.generation = null;
  },

  clear() {
    this._reset();
  },

  get lastId() {
    return this.entries.length ? this.entries[this.entries.length - 1].id : -1;
  },
};

// ──────────────────────────────────────────────────────────────────
// FILTER ENGINE
// ──────────────────────────────────────────────────────────────────
const FilterEngine = {
  levels:  new Set(['INFO', 'WARN', 'ERROR', 'DEBUG', 'TRACE']),
  text:    '',
  logger:  '',
  thread:  '',

  matches(e) {
    if (!this.levels.has(e.level)) return false;
    if (this.text   && !e.message.toLowerCase().includes(this.text))  return false;
    if (this.logger && !e.logger.toLowerCase().includes(this.logger))  return false;
    if (this.thread && !e.thread.toLowerCase().includes(this.thread))  return false;
    return true;
  },

  apply(list) { return list.filter(e => this.matches(e)); },

  toggleLevel(lv) {
    if (this.levels.has(lv)) this.levels.delete(lv);
    else                      this.levels.add(lv);
  },
};

// ──────────────────────────────────────────────────────────────────
// UTILS
// ──────────────────────────────────────────────────────────────────
const Utils = {
  pad2: n => String(n).padStart(2, '0'),
  pad3: n => String(n).padStart(3, '0'),

  fmtTime(ms) {
    const d = new Date(ms);
    return `${this.pad2(d.getHours())}:${this.pad2(d.getMinutes())}:${this.pad2(d.getSeconds())}.${this.pad3(d.getMilliseconds())}`;
  },

  fmtFull(ms) {
    return new Date(ms).toISOString().replace('T', ' ').slice(0, 23);
  },

  /** Shorten "com.example.foo.Bar" → "c.e.f.Bar" */
  shortLogger(name) {
    if (!name) return '';
    const parts = name.split('.');
    if (parts.length <= 2) return name;
    return parts.slice(0, -1).map(p => p[0]).join('.') + '.' + parts[parts.length - 1];
  },

  esc(s) {
    if (!s) return '';
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  },

  /** Escape HTML then wrap query matches in <mark>. */
  highlight(text, query) {
    const safe = this.esc(text);
    if (!query) return safe;
    const re = new RegExp(query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
    return safe.replace(re, m => `<mark>${m}</mark>`);
  },

  download(content, name, mime) {
    const a = Object.assign(document.createElement('a'), {
      href: URL.createObjectURL(new Blob([content], { type: mime })),
      download: name,
    });
    a.click();
    URL.revokeObjectURL(a.href);
  },

  copyText(text) {
    return navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject();
  },

  /** Format filtered Store entries as plain text lines. */
  asText(entries) {
    return entries.map(e =>
      `${Utils.fmtFull(e.timestamp)} [${e.level}] ${e.logger} [${e.thread}] ${e.message}`
    ).join('\n');
  },
};

// ──────────────────────────────────────────────────────────────────
// RENDERER
// ──────────────────────────────────────────────────────────────────
const Renderer = {
  list:       null,   // #log-list
  emptyState: null,   // #empty-state
  wrapLines:  false,
  showThread: true,
  domCount:   0,

  init() {
    this.list       = document.getElementById('log-list');
    this.emptyState = document.getElementById('empty-state');
  },

  _card(entry, animate) {
    const lv    = entry.level;
    const query = FilterEngine.text;
    const card  = document.createElement('div');

    card.className = `log-card lv-${lv}${animate ? ' new-log' : ''}`;
    card.dataset.id = entry.id;

    const timeStr   = Utils.fmtTime(entry.timestamp);
    const fullTime  = Utils.fmtFull(entry.timestamp);
    const shortLog  = Utils.shortLogger(entry.logger);
    const msgHtml   = Utils.highlight(entry.message, query);

    let stHtml = '';
    if (entry.stacktrace) {
      stHtml = `
        <button class="st-toggle" aria-expanded="false" aria-controls="st-${entry.id}">
          <span class="st-arrow">▶</span> Show stacktrace
        </button>
        <div class="st-body" id="st-${entry.id}" aria-hidden="true">
          <pre>${Utils.esc(entry.stacktrace)}</pre>
        </div>`;
    }

    const threadHtml = this.showThread
      ? `<span class="card-thread" title="${Utils.esc(entry.thread)}">${Utils.esc(entry.thread)}</span>`
      : '';

    card.innerHTML = `
      <div class="card-bar"></div>
      <div class="card-body">
        <div class="card-head">
          <span class="card-time" title="${fullTime}">${timeStr}</span>
          <span class="card-lv lv-${lv}">${lv}</span>
          <span class="card-logger" title="${Utils.esc(entry.logger)}" data-logger="${Utils.esc(entry.logger)}">${Utils.esc(shortLog)}</span>
          ${threadHtml}
        </div>
        <div class="card-msg${this.wrapLines ? '' : ' nowrap'}">${msgHtml}</div>
        ${stHtml}
      </div>`;

    // Stacktrace toggle
    const toggleBtn = card.querySelector('.st-toggle');
    if (toggleBtn) {
      toggleBtn.addEventListener('click', () => {
        const body     = card.querySelector('.st-body');
        const expanded = toggleBtn.getAttribute('aria-expanded') === 'true';
        toggleBtn.setAttribute('aria-expanded', String(!expanded));
        body.setAttribute('aria-hidden', String(expanded));
        body.classList.toggle('open', !expanded);
        const arrow = toggleBtn.querySelector('.st-arrow');
        if (arrow) arrow.style.transform = expanded ? '' : 'rotate(90deg)';
        toggleBtn.childNodes[1].textContent = expanded ? ' Show stacktrace' : ' Hide stacktrace';
      });
    }

    // Click logger → fill filter
    const loggerEl = card.querySelector('.card-logger');
    if (loggerEl) {
      loggerEl.addEventListener('click', () => {
        const input = document.getElementById('filter-logger');
        if (!input) return;
        input.value = entry.logger;
        FilterEngine.logger = entry.logger.toLowerCase();
        Renderer.rebuild();
      });
    }

    return card;
  },

  /** Append new entries — does NOT clear existing DOM. O(n_new). */
  append(entries, animate = true) {
    if (!entries.length) return;
    this.emptyState.style.display = 'none';

    const frag = document.createDocumentFragment();
    for (const e of entries) frag.appendChild(this._card(e, animate));
    this.list.appendChild(frag);
    this.domCount += entries.length;

    // Prune old DOM nodes from the top
    if (this.domCount > Config.maxDomCards + 100) {
      const excess = this.domCount - Config.maxDomCards;
      for (let i = 0; i < excess; i++) {
        if (this.list.firstChild) this.list.removeChild(this.list.firstChild);
      }
      this.domCount = Config.maxDomCards;
    }
  },

  /** Re-render everything from Store (called on filter change). */
  rebuild() {
    this.list.innerHTML = '';
    this.domCount = 0;
    const filtered = FilterEngine.apply(Store.entries);
    this.append(filtered, false);   // no new-log flash on rebuild
    CounterBar.set(filtered.length, Store.entries.length);
    if (!Store.entries.length) this.emptyState.style.display = 'flex';
  },

  clear() {
    this.list.innerHTML = '';
    this.domCount = 0;
    this.emptyState.style.display = 'flex';
  },
};

// ──────────────────────────────────────────────────────────────────
// STATS BAR  (navbar pills + sidebar chips both mirror FilterEngine)
// ──────────────────────────────────────────────────────────────────
const StatsBar = {
  LEVELS: ['INFO', 'WARN', 'ERROR', 'DEBUG', 'TRACE'],

  /** Push updated counts from Store.counts to both pill and chip UI. */
  refresh() {
    for (const lv of this.LEVELS) {
      const n = Store.counts[lv] || 0;
      const fmt = n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n);
      // Navbar pill count
      const pill = document.getElementById(`n-${lv}`);
      if (pill) pill.textContent = fmt;
      // Sidebar chip count
      const chip = document.getElementById(`ln-${lv}`);
      if (chip) chip.textContent = fmt;
    }
    this._syncActive();
  },

  _syncActive() {
    for (const lv of this.LEVELS) {
      const active = FilterEngine.levels.has(lv);
      // Navbar pill
      const pill = document.getElementById(`pill-${lv.toLowerCase()}`);
      if (pill) pill.classList.toggle('off', !active);
      // Sidebar chip label
      const label = document.querySelector(`label.lv-chip.${lv.toLowerCase()}`);
      if (label) {
        label.classList.toggle('active', active);
        const chk = label.querySelector('input');
        if (chk) chk.checked = active;
      }
    }
  },
};

// ──────────────────────────────────────────────────────────────────
// COUNTER BAR
// ──────────────────────────────────────────────────────────────────
const CounterBar = {
  set(visible, total) {
    const vc = document.getElementById('vis-count');
    const tc = document.getElementById('tot-count');
    if (vc) vc.textContent = visible.toLocaleString();
    if (tc) tc.textContent = total.toLocaleString();
  },
};

// ──────────────────────────────────────────────────────────────────
// STATUS INDICATOR
// ──────────────────────────────────────────────────────────────────
const StatusIndicator = {
  el:      null,
  textEl:  null,
  errCount: 0,

  init() {
    this.el     = document.getElementById('status-indicator');
    this.textEl = document.getElementById('si-text');
  },

  live()    { this._set('', 'LIVE');    this.errCount = 0; },
  paused()  { this._set('paused', 'PAUSED'); },
  error()   {
    this.errCount++;
    if (this.errCount >= 3) this._set('err', 'ERROR');
  },

  _set(cls, label) {
    if (!this.el) return;
    this.el.classList.remove('paused', 'err');
    if (cls) this.el.classList.add(cls);
    if (this.textEl) this.textEl.textContent = label;
  },
};

// ──────────────────────────────────────────────────────────────────
// TOAST
// ──────────────────────────────────────────────────────────────────
const Toast = {
  el:    null,
  timer: null,

  init() { this.el = document.getElementById('toast'); },

  show(msg, type = '') {
    if (!this.el) return;
    this.el.textContent = msg;
    this.el.className   = `toast show${type ? ' ' + type : ''}`;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.el.classList.remove('show'), 3000);
  },
};

// ──────────────────────────────────────────────────────────────────
// POLLER
// ──────────────────────────────────────────────────────────────────
const Poller = {
  timer:    null,
  paused:   false,
  autoScroll: true,

  async _tick() {
    try {
      const { entries, generation } = await API.fetchLogs(Store.lastId);
      const novel = Store.absorb(entries, generation);

      if (novel.length > 0) {
        const passing = novel.filter(e => FilterEngine.matches(e));
        Renderer.append(passing, true);
        StatsBar.refresh();
        CounterBar.set(
          FilterEngine.apply(Store.entries).length,
          Store.entries.length
        );
        if (this.autoScroll) App.scrollBottom();
      }

      StatusIndicator.live();
    } catch (err) {
      // Auth.onUnauthorized() already handles 401 — only non-401 errors land here.
      if (!String(err.message).includes('401')) {
        StatusIndicator.error();
        console.warn('[LogStream] poll error:', err);
      }
    }
  },

  start() {
    if (this.timer) return;
    this.paused = false;
    // BUG FIX: Do NOT fire an immediate _tick() here.
    //
    // The original code called _tick() synchronously before setInterval.
    // When called right after a successful login, the very first fetch went
    // out before the browser had fully persisted the session cookie from the
    // login response. That fetch returned 401, which called onUnauthorized()
    // and sent the user back to the login page — the "immediately returns to
    // login" redirect loop reported by the user.
    //
    // Deferring the first tick by pollMs (2 s) gives the browser time to
    // commit the cookie. Exception: when security is disabled (open access)
    // we fire one immediate tick so logs appear without a 2 s delay on
    // fresh page loads where there is no auth race condition to worry about.
    if (!Auth.securityEnabled) this._tick();
    this.timer = setInterval(() => this._tick(), Config.pollMs);
    StatusIndicator.live();
    this._syncBtn();
  },

  pause() {
    this.paused = true;
    clearInterval(this.timer);
    this.timer = null;
    StatusIndicator.paused();
    this._syncBtn();
  },

  resume() {
    this.paused = false;
    this.start();
  },

  toggle() { this.paused ? this.resume() : this.pause(); },

  _syncBtn() {
    const lbl = document.getElementById('pause-label');
    const ico  = document.getElementById('pause-icon');
    if (!lbl) return;
    if (this.paused) {
      lbl.textContent = 'Resume';
      if (ico) ico.setAttribute('d', 'M11.596 8.697l-6.363 3.692c-.54.313-1.233-.066-1.233-.697V4.308c0-.63.692-1.01 1.233-.696l6.363 3.692a.802.802 0 010 1.393z');
    } else {
      lbl.textContent = 'Pause';
      if (ico) ico.setAttribute('d', 'M5.5 3.5A1.5 1.5 0 017 5v6a1.5 1.5 0 01-3 0V5a1.5 1.5 0 011.5-1.5zm5 0A1.5 1.5 0 0112 5v6a1.5 1.5 0 01-3 0V5a1.5 1.5 0 011.5-1.5z');
    }
  },
};

// ──────────────────────────────────────────────────────────────────
// APP  (event wiring + bootstrap)
// ──────────────────────────────────────────────────────────────────
const App = {
  async init() {
    // Auth bootstrap MUST run first — it probes the API, hides the loading
    // screen, and shows either the dashboard or the login page.
    await Auth.bootstrap();

    Renderer.init();
    StatusIndicator.init();
    Toast.init();
    this._bindControls();
    this._bindFilters();
    this._bindKeyboard();
    this._bindScrollWatch();

    // Only start the poller when the user is already authenticated on this
    // page load (security disabled or valid existing session cookie).
    // When the login page is shown, the poller starts inside Auth._doLogin()
    // after the user successfully logs in.
    if (Auth._state === 'authenticated') {
      Poller.start();
    }
  },

  scrollBottom() {
    const panel = document.getElementById('log-panel');
    if (panel) panel.scrollTop = panel.scrollHeight;
  },

  // ── Toolbar controls ──────────────────────────────────────────
  _bindControls() {
    // Pause / Resume
    document.getElementById('btn-pause')?.addEventListener('click', () => Poller.toggle());

    // Clear
    document.getElementById('btn-clear')?.addEventListener('click', async () => {
      try { await API.clear(); } catch (_) { /* best-effort */ }
      Store.clear();
      Renderer.clear();
      StatsBar.refresh();
      CounterBar.set(0, 0);
      Toast.show('Log buffer cleared', 'ok');
    });

    // Export dropdown open/close
    const exportBtn  = document.getElementById('btn-export');
    const exportMenu = document.getElementById('export-menu');
    exportBtn?.addEventListener('click', e => {
      const open = exportMenu.classList.toggle('open');
      exportBtn.setAttribute('aria-expanded', String(open));
      e.stopPropagation();
    });
    document.addEventListener('click', () => {
      exportMenu?.classList.remove('open');
      exportBtn?.setAttribute('aria-expanded', 'false');
    });

    // JSON download
    document.getElementById('btn-json')?.addEventListener('click', () => {
      const data = JSON.stringify(FilterEngine.apply(Store.entries), null, 2);
      Utils.download(data, `logstream-${Date.now()}.json`, 'application/json');
      Toast.show('Downloaded JSON', 'ok');
    });

    // TXT download
    document.getElementById('btn-txt')?.addEventListener('click', () => {
      Utils.download(
        Utils.asText(FilterEngine.apply(Store.entries)),
        `logstream-${Date.now()}.txt`,
        'text/plain'
      );
      Toast.show('Downloaded TXT', 'ok');
    });

    // Copy
    document.getElementById('btn-copy')?.addEventListener('click', () => {
      Utils.copyText(Utils.asText(FilterEngine.apply(Store.entries)))
        .then(() => Toast.show('Copied to clipboard', 'ok'))
        .catch(() => Toast.show('Copy not supported', 'err-toast'));
    });

    // Navbar stat pills → toggle level
    document.querySelectorAll('.stat-pill[data-level]').forEach(btn => {
      btn.addEventListener('click', () => this._toggleLevel(btn.dataset.level));
    });
  },

  // ── Filters ───────────────────────────────────────────────────
  _bindFilters() {
    // Search
    const searchInput = document.getElementById('search-input');
    const searchClear = document.getElementById('search-clear');

    searchInput?.addEventListener('input', () => {
      FilterEngine.text = searchInput.value.toLowerCase();
      searchClear?.classList.toggle('hidden', !FilterEngine.text);
      Renderer.rebuild();
    });
    searchClear?.addEventListener('click', () => {
      searchInput.value = '';
      FilterEngine.text = '';
      searchClear.classList.add('hidden');
      Renderer.rebuild();
      searchInput.focus();
    });

    // Sidebar level chips
    document.querySelectorAll('.lv-chip input[type=checkbox]').forEach(chk => {
      chk.addEventListener('change', () => this._toggleLevel(chk.value));
    });

    // Logger filter
    document.getElementById('filter-logger')?.addEventListener('input', e => {
      FilterEngine.logger = e.target.value.toLowerCase();
      Renderer.rebuild();
    });

    // Thread filter
    document.getElementById('filter-thread')?.addEventListener('input', e => {
      FilterEngine.thread = e.target.value.toLowerCase();
      Renderer.rebuild();
    });

    // Toggles
    document.getElementById('chk-autoscroll')?.addEventListener('change', e => {
      Poller.autoScroll = e.target.checked;
    });

    document.getElementById('chk-wrap')?.addEventListener('change', e => {
      Renderer.wrapLines = e.target.checked;
      document.querySelectorAll('.card-msg').forEach(el => {
        el.classList.toggle('nowrap', !e.target.checked);
      });
    });

    document.getElementById('chk-thread')?.addEventListener('change', e => {
      Renderer.showThread = e.target.checked;
      Renderer.rebuild();
    });
  },

  _toggleLevel(lv) {
    FilterEngine.toggleLevel(lv);
    StatsBar._syncActive();
    Renderer.rebuild();
  },

  // ── Keyboard shortcuts ────────────────────────────────────────
  _bindKeyboard() {
    document.addEventListener('keydown', e => {
      const tag = e.target.tagName;
      const inInput = tag === 'INPUT' || tag === 'TEXTAREA';

      // Ctrl+F / Cmd+F → focus search
      if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault();
        document.getElementById('search-input')?.focus();
        return;
      }
      if (inInput) return;

      // Space → pause / resume
      if (e.key === ' ') { e.preventDefault(); Poller.toggle(); }
      // End → scroll to bottom
      if (e.key === 'End') { e.preventDefault(); this.scrollBottom(); }
      // Escape → clear search
      if (e.key === 'Escape') {
        const si = document.getElementById('search-input');
        if (si && si.value) {
          si.value = '';
          FilterEngine.text = '';
          document.getElementById('search-clear')?.classList.add('hidden');
          Renderer.rebuild();
        }
      }
    });
  },

  // ── Auto-scroll: disable when user scrolls up ─────────────────
  _bindScrollWatch() {
    const panel = document.getElementById('log-panel');
    if (!panel) return;
    panel.addEventListener('scroll', () => {
      const atBottom = panel.scrollHeight - panel.scrollTop - panel.clientHeight < 60;
      if (!atBottom && Poller.autoScroll) {
        Poller.autoScroll = false;
        const chk = document.getElementById('chk-autoscroll');
        if (chk) chk.checked = false;
      }
    }, { passive: true });
  },
};

// ── BOOTSTRAP ──────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => App.init());
