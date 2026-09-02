document.addEventListener('DOMContentLoaded', () => {
  // State
  let allDevices = [];
  let allMessages = [];
  let selectedUserForModal = null;
  let soundEnabled = true;
  let sseEventSource = null;

  // DOM Elements
  const connectionStatusEl = document.getElementById('connection-status');
  const activeDevicesStatEl = document.getElementById('stat-active-devices');
  const totalOtpsStatEl = document.getElementById('stat-total-otps');
  const clockEl = document.getElementById('live-clock');
  const soundToggleBtn = document.getElementById('btn-sound-toggle');
  
  const userSearchInput = document.getElementById('user-search-input');
  const usersGrid = document.getElementById('users-grid');
  const emptyDevicesState = document.getElementById('empty-devices-state');
  const devicesCountBadge = document.getElementById('devices-count-badge');
  
  const liveFeedContainer = document.getElementById('live-feed-container');
  const emptyOtpState = document.getElementById('empty-otp-state');
  
  const btnSimulateDevice = document.getElementById('btn-simulate-device');
  const btnSimulateSms = document.getElementById('btn-simulate-sms');

  // Modal Elements
  const userHistoryModal = document.getElementById('user-history-modal');
  const modalUserTitle = document.getElementById('modal-user-title');
  const modalUserSubtitle = document.getElementById('modal-user-subtitle');
  const modalHistoryList = document.getElementById('modal-history-list');
  const modalSearchInput = document.getElementById('modal-search-input');
  const modalCountBadge = document.getElementById('modal-count-badge');
  const btnCloseModal = document.getElementById('btn-close-modal');

  // Initialization
  initClock();
  initSoundToggle();
  fetchDevices();
  fetchMessages();
  connectRealtimeStream();
  setupEventListeners();

  // Digital Clock
  function initClock() {
    setInterval(() => {
      clockEl.textContent = new Date().toLocaleTimeString();
    }, 1000);
  }

  // Audio Synthesizer Beep Chime
  function playNotificationSound() {
    if (!soundEnabled) return;
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(1320, ctx.currentTime + 0.15);
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.25);
    } catch (e) {}
  }

  function initSoundToggle() {
    soundToggleBtn.addEventListener('click', () => {
      soundEnabled = !soundEnabled;
      soundToggleBtn.innerHTML = soundEnabled ? `<span>🔊</span> Sound ON` : `<span>🔇</span> Sound OFF`;
      if (soundEnabled) playNotificationSound();
    });
  }

  // Real-Time SSE Stream
  function connectRealtimeStream() {
    if (sseEventSource) sseEventSource.close();
    sseEventSource = new EventSource('/api/stream');

    sseEventSource.onopen = () => {
      connectionStatusEl.textContent = 'CONNECTED';
      connectionStatusEl.className = 'stat-value text-green';
    };

    sseEventSource.onerror = () => {
      connectionStatusEl.textContent = 'RECONNECTING';
      connectionStatusEl.className = 'stat-value text-warning';
    };

    sseEventSource.addEventListener('new_otp', (e) => {
      const msg = JSON.parse(e.data);
      allMessages.unshift(msg);
      
      // Update UI components
      renderLiveFeedItem(msg);
      fetchDevices(); // Refresh notification counts on user cards
      updateStats();
      playNotificationSound();

      // If history modal is currently open for this user, refresh modal list
      if (selectedUserForModal && 
         (selectedUserForModal.departmentName === msg.departmentName || selectedUserForModal.mobileNumber === msg.mobileNumber)) {
        renderModalHistory();
      }

      showToast(`Notification for ${msg.departmentName}: ${msg.sender}`);
    });

    sseEventSource.addEventListener('device_registered', () => {
      fetchDevices();
    });

    sseEventSource.addEventListener('device_ping', () => {
      fetchDevices();
    });
  }

  // Fetch Devices & Messages
  async function fetchDevices() {
    try {
      const res = await fetch('/api/devices');
      if (res.ok) {
        allDevices = await res.json();
        renderUsersGrid();
        updateStats();
      }
    } catch (e) {}
  }

  async function fetchMessages() {
    try {
      const res = await fetch('/api/messages');
      if (res.ok) {
        allMessages = await res.json();
        renderLiveFeed();
        if (selectedUserForModal) renderModalHistory();
        updateStats();
      }
    } catch (e) {}
  }

  // ─────────────────────────────────────────────
  // RENDER USERS DIRECTORY GRID (Primary Focus)
  // ─────────────────────────────────────────────
  function renderUsersGrid() {
    const searchTerm = userSearchInput.value.toLowerCase().trim();

    const filteredUsers = allDevices.filter(dev => {
      return !searchTerm ||
        dev.departmentName.toLowerCase().includes(searchTerm) ||
        dev.deviceName.toLowerCase().includes(searchTerm) ||
        dev.mobileNumber.toLowerCase().includes(searchTerm) ||
        dev.address.toLowerCase().includes(searchTerm);
    });

    devicesCountBadge.textContent = `${allDevices.length} Registered`;

    if (filteredUsers.length === 0) {
      emptyDevicesState.style.display = 'flex';
      usersGrid.querySelectorAll('.user-card').forEach(el => el.remove());
      return;
    }

    emptyDevicesState.style.display = 'none';
    usersGrid.querySelectorAll('.user-card').forEach(el => el.remove());

    filteredUsers.forEach(dev => {
      // Calculate ALL notifications for this specific user
      const userNotifications = allMessages.filter(m => 
        m.deviceId === dev.deviceId || 
        m.mobileNumber === dev.mobileNumber || 
        m.departmentName.toLowerCase() === dev.departmentName.toLowerCase()
      );

      const card = document.createElement('div');
      card.className = 'user-card';
      card.innerHTML = `
        <div class="user-card-header">
          <div style="display: flex; align-items: center;">
            <div class="user-avatar">👤</div>
            <div class="user-details">
              <span class="user-name">${escapeHTML(dev.departmentName)}</span>
              <span class="user-mobile">📞 ${escapeHTML(dev.mobileNumber)}</span>
            </div>
          </div>
          <span class="user-status-pill ${dev.isOnline ? 'online' : 'offline'}">
            ${dev.isOnline ? 'ONLINE' : 'OFFLINE'}
          </span>
        </div>

        <div class="user-card-body">
          <span>📍 <strong>Address:</strong> ${escapeHTML(dev.address || 'Main Office')}</span>
          <span>🕒 <strong>Last Active:</strong> ${formatTimeAgo(dev.lastSeen)}</span>
        </div>

        <div class="user-card-footer">
          <span class="noti-badge">📩 ${userNotifications.length} Notifications</span>
          <span class="btn-view-history">👁️ View All Notifications &rarr;</span>
        </div>
      `;

      // Click event to open User Notification History Modal
      card.addEventListener('click', () => {
        openUserHistoryModal(dev);
      });

      usersGrid.appendChild(card);
    });
  }

  // ─────────────────────────────────────────────
  // RENDER LIVE FEED COLUMN
  // ─────────────────────────────────────────────
  function renderLiveFeed() {
    if (allMessages.length === 0) {
      emptyOtpState.style.display = 'flex';
      liveFeedContainer.querySelectorAll('.live-card').forEach(el => el.remove());
      return;
    }

    emptyOtpState.style.display = 'none';
    liveFeedContainer.querySelectorAll('.live-card').forEach(el => el.remove());

    allMessages.slice(0, 15).forEach(msg => {
      renderLiveFeedItem(msg, false);
    });
  }

  function renderLiveFeedItem(msg, isNew = true) {
    emptyOtpState.style.display = 'none';
    const card = document.createElement('div');
    card.className = `live-card ${msg.otp ? 'has-otp' : ''}`;
    
    card.innerHTML = `
      <div class="live-card-top">
        <span class="live-dept">${escapeHTML(msg.departmentName)}</span>
        <span style="color: var(--text-dim);">${formatTimeAgo(msg.receivedAt || msg.timestamp)}</span>
      </div>
      
      <div style="font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">SENDER: <strong>${escapeHTML(msg.sender)}</strong></div>

      ${msg.otp ? `
        <div class="live-otp-box">
          <div>
            <div style="font-size: 10px; color: var(--text-muted);">DETECTED OTP</div>
            <span class="live-otp-code">${escapeHTML(msg.otp)}</span>
          </div>
          <button class="btn-copy-sm" onclick="copyToClipboard('${msg.otp}', this)">COPY OTP</button>
        </div>
      ` : ''}

      <div style="font-size: 12px; color: #cbd5e1; line-height: 1.4; word-break: break-word; margin-top: 4px;">
        ${escapeHTML(msg.body)}
      </div>
    `;

    if (isNew) {
      liveFeedContainer.insertBefore(card, liveFeedContainer.firstChild);
    } else {
      liveFeedContainer.appendChild(card);
    }
  }

  // ─────────────────────────────────────────────
  // USER ALL NOTIFICATION HISTORY MODAL HANDLERS
  // ─────────────────────────────────────────────
  function openUserHistoryModal(device) {
    selectedUserForModal = device;
    modalUserTitle.textContent = `📜 ${device.departmentName} - All Notifications`;
    modalUserSubtitle.textContent = `Mobile: ${device.mobileNumber} | Location: ${device.address}`;
    modalSearchInput.value = '';
    
    userHistoryModal.classList.add('active');
    renderModalHistory();
  }

  function renderModalHistory() {
    if (!selectedUserForModal) return;

    const searchTerm = modalSearchInput.value.toLowerCase().trim();

    // Filter messages specifically for selected user
    const userMessages = allMessages.filter(m => {
      const isUserMsg = m.deviceId === selectedUserForModal.deviceId ||
                         m.mobileNumber === selectedUserForModal.mobileNumber ||
                         m.departmentName.toLowerCase() === selectedUserForModal.departmentName.toLowerCase();

      const matchesSearch = !searchTerm ||
        m.body.toLowerCase().includes(searchTerm) ||
        m.sender.toLowerCase().includes(searchTerm) ||
        (m.otp && m.otp.includes(searchTerm));

      return isUserMsg && matchesSearch;
    });

    modalCountBadge.textContent = `${userMessages.length} Total Notifications`;

    if (userMessages.length === 0) {
      modalHistoryList.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <h3>No Notifications Found</h3>
          <p>No SMS or notifications have been received for this user yet.</p>
        </div>
      `;
      return;
    }

    modalHistoryList.innerHTML = '';

    userMessages.forEach(msg => {
      const item = document.createElement('div');
      item.className = 'history-item-card';
      item.innerHTML = `
        <div class="history-item-top">
          <span>🏦 Sender / App: <strong>${escapeHTML(msg.sender)}</strong></span>
          <span>🕒 ${new Date(msg.receivedAt || msg.timestamp).toLocaleString()}</span>
        </div>

        ${msg.otp ? `
          <div class="history-otp-banner">
            <div>
              <div style="font-size: 10px; color: var(--text-muted); letter-spacing: 1px;">DETECTED OTP CODE</div>
              <div class="history-otp-code">${escapeHTML(msg.otp)}</div>
            </div>
            <button class="btn-copy" onclick="copyToClipboard('${msg.otp}', this)">
              📋 COPY OTP
            </button>
          </div>
        ` : ''}

        <div style="font-size: 13px; color: #cbd5e1; line-height: 1.5; word-break: break-word; margin-top: 6px;">
          ${escapeHTML(msg.body)}
        </div>

        <div style="display: flex; justify-content: flex-end; margin-top: 8px;">
          <button class="btn-outline btn-sm" onclick="copyToClipboard('${escapeHTML(msg.body).replace(/'/g, "\\'")}', this)">
            📋 Copy Full Message Text
          </button>
        </div>
      `;
      modalHistoryList.appendChild(item);
    });
  }

  // Event Listeners
  function setupEventListeners() {
    userSearchInput.addEventListener('input', renderUsersGrid);
    modalSearchInput.addEventListener('input', renderModalHistory);

    btnCloseModal.addEventListener('click', () => {
      userHistoryModal.classList.remove('active');
      selectedUserForModal = null;
    });

    userHistoryModal.addEventListener('click', (e) => {
      if (e.target === userHistoryModal) {
        userHistoryModal.classList.remove('active');
        selectedUserForModal = null;
      }
    });

    // Simulate Register User
    btnSimulateDevice.addEventListener('click', async () => {
      const sampleDepts = [
        { name: "Accounts Dept", phone: "+91 98765 11111", address: "Floor 2 - Room 201" },
        { name: "Sales Team", phone: "+91 98765 22222", address: "Floor 1 - Main Desk" },
        { name: "HR Department", phone: "+91 98765 33333", address: "Floor 3 - Cabin 4" },
        { name: "Operations", phone: "+91 98765 44444", address: "Ground Floor - Gate 1" }
      ];
      const sample = sampleDepts[Math.floor(Math.random() * sampleDepts.length)];

      try {
        await fetch('/api/register-device', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            departmentName: sample.name,
            mobileNumber: sample.phone,
            address: sample.address,
            role: 'SOURCE'
          })
        });
        fetchDevices();
      } catch (e) {}
    });

    // Simulate Notification (Both OTP and Non-OTP Notifications)
    btnSimulateSms.addEventListener('click', async () => {
      if (allDevices.length === 0) {
        btnSimulateDevice.click();
        await new Promise(r => setTimeout(r, 300));
      }

      const randomDev = allDevices[Math.floor(Math.random() * allDevices.length)];
      
      const sampleNotifications = [
        { sender: 'HDFC-BANK', body: 'Your OTP for online payment of Rs. 14,500 at Amazon is 839201. Valid for 10 mins.' },
        { sender: 'ICICI-ALERT', body: 'Dear Customer, A/c XX8920 has been debited by Rs 2,500.00 on 01-Sep-26. Info: UPI/VendorPay.' },
        { sender: 'SBI-BANK', body: 'Salary credit of Rs 65,000.00 done in A/c XX4091 on 01-Sep-26. Available Bal: Rs 1,42,900.' },
        { sender: 'SWIGGY', body: 'Your order #91024 has been delivered to Gate 2 reception by delivery partner.' },
        { sender: 'RAZORPAY', body: 'Use 492015 as your verification code to complete sign-in to Razorpay Dashboard.' }
      ];

      const noti = sampleNotifications[Math.floor(Math.random() * sampleNotifications.length)];

      try {
        await fetch('/api/send-sms', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            deviceId: randomDev.deviceId,
            departmentName: randomDev.departmentName,
            mobileNumber: randomDev.mobileNumber,
            address: randomDev.address,
            sender: noti.sender,
            body: noti.body,
            timestamp: new Date().toISOString()
          })
        });
      } catch (e) {}
    });
  }

  // Stats Counters
  function updateStats() {
    const activeCount = allDevices.filter(d => d.isOnline).length;
    activeDevicesStatEl.textContent = `${activeCount} / ${allDevices.length}`;
    totalOtpsStatEl.textContent = allMessages.length;
  }

  // Global Copy Helper
  window.copyToClipboard = function(text, btnElement) {
    navigator.clipboard.writeText(text).then(() => {
      const originalText = btnElement.innerHTML;
      btnElement.innerHTML = `✓ COPIED!`;
      btnElement.style.background = '#059669';
      setTimeout(() => {
        btnElement.innerHTML = originalText;
        btnElement.style.background = '';
      }, 2000);
    });
  };

  function formatTimeAgo(isoString) {
    if (!isoString) return 'Just now';
    const date = new Date(isoString);
    const diffSecs = Math.floor((Date.now() - date.getTime()) / 1000);
    if (diffSecs < 10) return 'Just now';
    if (diffSecs < 60) return `${diffSecs}s ago`;
    if (diffSecs < 3600) return `${Math.floor(diffSecs / 60)}m ago`;
    return date.toLocaleTimeString();
  }

  function escapeHTML(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag));
  }

  function showToast(message) {
    const toastContainer = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `<span>🔔</span> <span>${escapeHTML(message)}</span>`;
    toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(50px)';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }
});
