// Live fleet events.
//
// Kept in an external file rather than inline because the Content-Security-Policy
// sets script-src 'self': no inline script runs, which removes a whole class of XSS.
(function () {
  const feed = document.getElementById('event-feed');
  const status = document.getElementById('stream-status');
  if (!feed || !window.EventSource) return;

  const source = new EventSource('/events/stream');

  source.addEventListener('open', () => {
    status.textContent = 'En vivo';
    status.className = 'ks-chip ks-chip--ok';
  });

  source.addEventListener('error', () => {
    status.textContent = 'Reconectando';
    status.className = 'ks-chip ks-chip--warn';
  });

  source.addEventListener('fleet', (message) => {
    const event = JSON.parse(message.data);

    const row = document.createElement('li');
    row.style.padding = '6px 0';
    row.style.borderBottom = '1px solid var(--ks-border)';

    // textContent, never innerHTML: the subject comes from a device-supplied serial
    // number, and building markup out of it would be a stored XSS waiting to happen.
    const action = document.createElement('span');
    action.className = 'ks-mono';
    action.style.color = 'var(--ks-accent-hover)';
    action.textContent = event.action;

    const subject = document.createElement('span');
    subject.style.color = 'var(--ks-text)';
    subject.textContent = ' ' + event.subject;

    const detail = document.createElement('span');
    detail.style.color = 'var(--ks-text-dim)';
    detail.textContent = ' · ' + event.detail;

    row.append(action, subject, detail);

    if (feed.firstChild && feed.firstChild.textContent.startsWith('Esperando')) {
      feed.innerHTML = '';
    }
    feed.prepend(row);

    while (feed.childElementCount > 20) {
      feed.removeChild(feed.lastChild);
    }
  });
})();
