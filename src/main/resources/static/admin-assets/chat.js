(function () {
  const messagesEl = document.getElementById('messages');
  const confirmArea = document.getElementById('confirm-area');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('message-input');
  const csrfToken = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

  function appendMessage(role, text) {
    const div = document.createElement('div');
    div.className = 'message message-' + role;
    div.textContent = text;
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  async function postJson(url, body) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
      body: JSON.stringify(body)
    });
    return res.json();
  }

  function render(data) {
    if (data.type === 'MESSAGE') {
      appendMessage('assistant', data.text);
    } else if (data.type === 'CONFIRMATION') {
      renderConfirmation(data);
    } else {
      appendMessage('error', data.error || '오류가 발생했습니다.');
    }
  }

  function renderConfirmation(data) {
    confirmArea.innerHTML = '';
    const card = document.createElement('div');
    card.className = 'confirm-card';

    const summary = document.createElement('p');
    summary.className = 'confirm-summary';
    summary.textContent = data.summary;

    const buttons = document.createElement('div');
    buttons.className = 'confirm-buttons';

    const approve = document.createElement('button');
    approve.textContent = '실행';
    approve.className = 'btn-approve';
    approve.onclick = async function () {
      confirmArea.innerHTML = '';
      appendMessage('assistant', '작업을 실행 중입니다...');
      try {
        const result = await postJson('/admin/api/chat/confirm', { actionId: data.actionId });
        render(result);
      } catch (err) {
        appendMessage('error', '실행 요청에 실패했습니다.');
      }
    };

    const cancel = document.createElement('button');
    cancel.textContent = '취소';
    cancel.className = 'btn-cancel';
    cancel.onclick = function () {
      confirmArea.innerHTML = '';
      appendMessage('assistant', '작업을 취소했습니다.');
    };

    buttons.appendChild(approve);
    buttons.appendChild(cancel);
    card.appendChild(summary);
    card.appendChild(buttons);
    confirmArea.appendChild(card);
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    const message = input.value.trim();
    if (!message) return;
    appendMessage('user', message);
    input.value = '';
    confirmArea.innerHTML = '';
    try {
      const result = await postJson('/admin/api/chat', { message: message });
      render(result);
    } catch (err) {
      appendMessage('error', '요청에 실패했습니다.');
    }
  });
})();
