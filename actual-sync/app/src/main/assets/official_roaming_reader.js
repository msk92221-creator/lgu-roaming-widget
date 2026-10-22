(() => {
  const normalize = value => String(value || '').replace(/[\u00a0\u202f]/g, ' ')
    .replace(/\s+/g, ' ').trim();
  const host = location.hostname.toLowerCase();
  const carrierHost = value => value === 'lguplus.com' || value.endsWith('.lguplus.com');
  const output = (state, stage, remaining = '', total = '') => JSON.stringify({
    state, stage, remaining, total, layout: host === 'm.lguplus.com' ? 'mobile' : 'desktop',
    // Public first-party JS asset references only; never query strings, fragments,
    // hydration data, page HTML, API parameters, cookies or credentials.
    assets: state === 'ready' ? [] : Array.from(document.querySelectorAll('script[src]'))
      .map(script => {
        try {
          const url = new URL(script.getAttribute('src'), location.href);
          if (url.protocol !== 'https:' || !['m.lguplus.com', 'www.lguplus.com'].includes(url.hostname)
              || (url.port && url.port !== '443') || url.username || url.password
              || url.pathname.length > 220 || url.pathname.includes('..')
              || !/^\/(?:static|_nuxt|_next|assets)\/[A-Za-z0-9_./-]+\.js$/.test(url.pathname)) return '';
          return url.origin + url.pathname;
        } catch (_) { return ''; }
      }).filter(Boolean).filter((value, index, all) => all.indexOf(value) === index)
      .sort((a, b) => Number(/roaming|my-roaming/.test(b)) - Number(/roaming|my-roaming/.test(a)))
      .slice(0, 12)
  });
  if (location.protocol !== 'https:' || (location.port !== '' && location.port !== '443'))
    return output('unsupported', 'origin');
  if (host === 'account.lguplus.com' || /^\/login(?:\/|$)/.test(location.pathname))
    return output('login', 'login');
  if (!['www.lguplus.com', 'm.lguplus.com'].includes(host)
      || !/^\/plan\/roaming\/my-roaming\/?$/.test(location.pathname))
    return output('unsupported', 'origin');

  const hidden = element => {
    for (let node = element; node && node.nodeType === 1; node = node.parentElement) {
      if (node.hidden || node.getAttribute('aria-hidden') === 'true') return true;
      const style = typeof getComputedStyle === 'function' ? getComputedStyle(node) : node.style;
      if (style && (style.display === 'none' || style.visibility === 'hidden'
          || style.visibility === 'collapse')) return true;
    }
    return false;
  };
  let remaining = '', total = '';
  if (host === 'm.lguplus.com') {
    // The phone screenshot's exact aggregate row, independent of mobile CSS class names.
    // Do not read the basic component, a plan name, a promo, or a whole plan card.
    const amount = '((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?\\s*(?:TB|GB|MB))';
    const pair = new RegExp('^총\\s*데이터\\s*' + amount + '\\s*남음\\s*(?:[/|│∣]\\s*)?'
      + amount + '$', 'i');
    const rows = new Map();
    const nodes = document.querySelectorAll('p,span,dt,dd,th,td,strong,b,em,label,div,h2,h3,h4,h5,h6');
    if (nodes.length > 6000) return output('unsupported', 'node_limit');
    for (const label of nodes) {
      if (!/^총\s*데이터$/.test(normalize(label.textContent)) || hidden(label)) continue;
      for (let row = label, depth = 0; row && depth < 7; row = row.parentElement, depth++) {
        if (row === document.body || row === document.documentElement || hidden(row)) break;
        const text = normalize(row.textContent);
        if (text.length > 180 || /기본\s*데이터|추가\s*데이터|충전\s*데이터/.test(text)) break;
        const match = pair.exec(text);
        if (match) {
          rows.set(row, [match[1], match[2]]);
          break;
        }
      }
    }
    if (!rows.size) return output('loading', 'mobile_total_missing');
    if (rows.size !== 1) return output('ambiguous', 'multiple_total_rows');
    [remaining, total] = rows.values().next().value;
  } else {
    const cards = Array.from(document.querySelectorAll('.white-box.data-use-status')).filter(card => !hidden(card));
    if (!cards.length) return output('loading', 'desktop_total_missing');
    if (cards.length !== 1) return output('ambiguous', 'multiple_total_rows');
    const card = cards[0];
    const titles = card.querySelectorAll('.data-title-area > h6');
    const values = card.querySelectorAll('.data-title-area .data-info .point');
    const graph = card.querySelector('.bar-graph-area');
    const totals = graph ? graph.querySelectorAll('.data-legend .total-data') : [];
    if (titles.length !== 1 || values.length !== 1 || totals.length !== 1)
      return output('loading', 'desktop_total_missing');
    if (/무제한|일일|하루/.test(titles[0].textContent || ''))
      return output('unsupported', 'unsupported_plan');
    remaining = normalize(values[0].textContent);
    total = normalize(totals[0].textContent);
  }

  const completed = performance.getEntriesByType('resource').some(entry => {
    try {
      const url = new URL(entry.name);
      return url.protocol === 'https:' && carrierHost(url.hostname)
        && (!url.port || url.port === '443')
        && url.pathname === '/uhdc/fo/myin/roam/v1/data-rmndlist' && entry.responseEnd > 0
        && (!entry.responseStatus || (entry.responseStatus >= 200 && entry.responseStatus < 300));
    } catch (_) { return false; }
  });
  if (!completed) return output('loading', 'waiting_balance_response');
  return output('ready', 'ready', remaining, total);
})()
