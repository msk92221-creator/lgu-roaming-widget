'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { parseHTML } = require('linkedom');

// Execute the production asset itself. Fixtures are synthetic and contain no account data.
const reader = new vm.Script(fs.readFileSync(path.join(__dirname,
  '../app/src/main/assets/official_roaming_reader.js'), 'utf8'),
  { filename: 'official_roaming_reader.js' });
const MOBILE = 'https://m.lguplus.com/plan/roaming/my-roaming';
const DESKTOP = 'https://www.lguplus.com/plan/roaming/my-roaming';
const BALANCE = '/uhdc/fo/myin/roam/v1/data-rmndlist';

function completed(overrides = {}) {
  return {
    name: `https://m.lguplus.com${BALANCE}?entrNo=fixture-only-identifier`,
    responseEnd: 100,
    responseStatus: 200,
    ...overrides
  };
}

function mobileRow(value = '43.9GB남음 / 49GB', attrs = '') {
  return `<div class="meter-row" ${attrs}><span>총 데이터</span><strong>${value}</strong></div>`;
}

function desktopCard(remaining = '44GB', total = '49GB', title = '로밍패스 49GB', attrs = '') {
  return `<div class="white-box data-use-status" ${attrs}>
    <div class="data-title-area"><h6>${title}</h6>
      <div class="data-info"><strong class="point">${remaining}</strong> 사용할 수 있어요</div>
    </div>
    <div class="bar-graph-area"><div class="data-legend"><span class="total-data">${total}</span></div></div>
    <div class="data-text-area"><div class="row-line">기본 데이터 <span class="value">42GB 남음 49GB</span></div></div>
  </div>`;
}

function read(html, options = {}) {
  const { document } = parseHTML(`<!DOCTYPE html><html><head></head><body>${html}</body></html>`);
  // Inline CSS/ancestor visibility is enough for these fixtures. No viewport or geometry API
  // is supplied: the reader must also work in an unattached, hidden Android WebView.
  const result = reader.runInNewContext({
    document,
    location: new URL(options.url || MOBILE),
    URL,
    performance: {
      getEntriesByType(type) {
        assert.equal(type, 'resource');
        return options.entries === undefined ? [completed()] : options.entries;
      }
    },
    getComputedStyle(element) {
      return { display: element.style.display, visibility: element.style.visibility };
    }
  }, { timeout: 1000 });
  return JSON.parse(result);
}

function assertReady(result, remaining = '43.9GB', total = '49GB') {
  assert.equal(result.state, 'ready');
  assert.equal(result.stage, 'ready');
  assert.equal(result.remaining, remaining);
  assert.equal(result.total, total);
}

test('reads the screenshot-shaped mobile aggregate row', () => {
  const result = read(`<main><h1>해외로밍 이용현황</h1><section><h2>로밍패스 49GB</h2>
    ${mobileRow()}<div>기본 데이터 <span>43.9GB 남음 | 49GB</span></div>
    <p>만 34세 이하 청년 충전 1GB 추가 제공</p></section></main>`);
  assertReady(result);
  assert.equal(result.layout, 'mobile');
});

test('aggregate wins over a different basic component and charging promo', () => {
  assertReady(read(`<section>${mobileRow('43.9GB남음 / 50GB')}
    <div>기본 데이터 42.9GB 남음 | 49GB</div><div>충전 데이터 1GB</div>
    <p>충전 1GB 추가 제공</p></section>`), '43.9GB', '50GB');
});

test('nested label wrappers resolve to the same physical row only once', () => {
  assertReady(read(`<div class="meter-row"><div><strong><span>총 데이터</span></strong></div>
    <div><span>43.9GB남음</span><span> / 49GB</span></div></div>`));
});

test('split label text is normalized without depending on CSS class names', () => {
  assertReady(read('<section><div><span>총 <b>데이터</b></span><span>43.9GB남음 / 49GB</span></div></section>'));
});

test('NBSP and line breaks do not change the row meaning', () => {
  assertReady(read(mobileRow('43.9GB\u00a0남음\n/\u202f49GB')));
});

for (const separator of ['/', '|', '│', '∣', '']) {
  test(`recognizes the explicit remaining/total pair with separator ${JSON.stringify(separator)}`, () => {
    assertReady(read(mobileRow(`43.9GB남음 ${separator} 49GB`)));
  });
}

test('zero remaining is a real numeric display, not a guessed exhausted screen', () => {
  assertReady(read(mobileRow('0GB남음 / 49GB')), '0GB');
});

test('preserves displayed decimal precision for Java validation', () => {
  assertReady(read(mobileRow('43.90GB남음 / 49.00GB')), '43.90GB', '49.00GB');
});

test('two equal physical aggregate rows are ambiguous rather than value-deduplicated', () => {
  const result = read(mobileRow() + mobileRow());
  assert.equal(result.state, 'ambiguous');
  assert.equal(result.stage, 'multiple_total_rows');
  assert.equal(result.remaining, '');
});

test('two different physical aggregate rows are ambiguous', () => {
  assert.equal(read(mobileRow() + mobileRow('20GB남음 / 30GB')).state, 'ambiguous');
});

for (const attributes of ['hidden', 'aria-hidden="true"', 'style="display:none"',
  'style="visibility:hidden"', 'style="visibility:collapse"']) {
  test(`ignores an explicitly hidden cloned row: ${attributes}`, () => {
    assertReady(read(mobileRow('1GB남음 / 2GB', attributes) + mobileRow()));
  });
}

test('hidden ancestors hide their descendants as well', () => {
  assertReady(read(`<div aria-hidden="true"><div>${mobileRow('1GB남음 / 2GB')}</div></div>${mobileRow()}`));
});

test('requires neither DOM viewport geometry nor visibilityState', () => {
  assertReady(read(mobileRow()));
});

test('basic data alone never substitutes for an aggregate row', () => {
  const result = read('<div>기본 데이터 <span>43.9GB 남음 | 49GB</span></div>');
  assert.equal(result.state, 'loading');
  assert.equal(result.stage, 'mobile_total_missing');
});

test('a total label with only a basic-data whole card is not a meter pair', () => {
  const result = read('<section><h2>로밍패스 49GB</h2><span>총 데이터</span>'
    + '<div>기본 데이터 43.9GB 남음 / 49GB</div><button>데이터 충전하기</button></section>');
  assert.notEqual(result.state, 'ready');
});

test('a whole card cannot fill in a missing minimal aggregate row', () => {
  const result = read('<section><h2>로밍패스 49GB</h2><span>총 데이터</span>'
    + '<p>사용중 요금제</p><div>43.9GB남음 / 49GB</div><p>충전 1GB 추가 제공</p></section>');
  assert.notEqual(result.state, 'ready');
});

test('an aggregate row contaminated by another meter is refused', () => {
  assert.notEqual(read(mobileRow('43.9GB남음 / 49GB 1GB남음 / 2GB')).state, 'ready');
});

test('the label must be exactly total data, not a similar label', () => {
  assert.notEqual(read('<div><span>이번달 총 데이터 사용량</span><span>43.9GB남음 / 49GB</span></div>').state, 'ready');
});

test('plan-name capacity and unlimited text cannot become remaining data', () => {
  assert.notEqual(read('<h2>로밍패스 49GB</h2><div>총 데이터 무제한</div>').state, 'ready');
});

test('keeps the previously verified desktop aggregate selectors', () => {
  const result = read(desktopCard(), { url: DESKTOP });
  assertReady(result, '44GB');
  assert.equal(result.layout, 'desktop');
});

test('desktop tethering graph is not substituted for the first aggregate graph', () => {
  const fixture = desktopCard().replace('</div>\n    <div class="data-text-area">',
    '</div><div class="bar-graph-area"><div class="data-legend"><span class="total-data">10GB</span></div></div>'
    + '<div class="data-text-area">');
  assertReady(read(fixture, { url: DESKTOP }), '44GB');
});

test('a hidden desktop clone does not create false ambiguity', () => {
  assertReady(read(desktopCard('1GB', '2GB', '로밍패스', 'style="display:none"')
    + desktopCard(), { url: DESKTOP }), '44GB');
});

test('equal visible desktop cards remain ambiguous', () => {
  assert.equal(read(desktopCard() + desktopCard(), { url: DESKTOP }).state, 'ambiguous');
});

test('desktop unlimited and daily plans remain unsupported', () => {
  for (const plan of ['로밍 무제한', '일일 로밍', '하루 데이터']) {
    assert.equal(read(desktopCard('44GB', '49GB', plan), { url: DESKTOP }).state, 'unsupported');
  }
});

for (const url of ['http://m.lguplus.com/plan/roaming/my-roaming',
  'https://m.lguplus.com:8443/plan/roaming/my-roaming',
  'https://m.lguplus.com.evil.invalid/plan/roaming/my-roaming',
  'https://m.lguplus.com/plan/roaming/my-roaming-other',
  'https://m.lguplus.com/']) {
  test(`refuses an unsupported page origin/path: ${url}`, () => {
    assert.equal(read(mobileRow(), { url }).state, 'unsupported');
  });
}

test('recognizes normal official login without inspecting login fields', () => {
  const result = read('<input type="password" value="fixture-only-secret">', {
    url: 'https://account.lguplus.com/login?code=fixture-only-token'
  });
  assert.equal(result.state, 'login');
  assert.doesNotMatch(JSON.stringify(result), /fixture-only-secret|fixture-only-token/);
});

test('a complete total row cannot be saved while its balance response is pending', () => {
  const result = read(mobileRow(), { entries: [completed({ responseEnd: 0 })] });
  assert.equal(result.state, 'loading');
  assert.equal(result.stage, 'waiting_balance_response');
  assert.equal(result.remaining, '');
});

test('a complete total row without a balance resource does not become success', () => {
  assert.equal(read(mobileRow(), { entries: [] }).stage, 'waiting_balance_response');
});

for (const status of [400, 401, 403, 404, 500]) {
  test(`HTTP ${status} balance resource does not satisfy freshness`, () => {
    assert.notEqual(read(mobileRow(), { entries: [completed({ responseStatus: status })] }).state, 'ready');
  });
}

for (const name of [
  `https://m.lguplus.com.evil.invalid${BALANCE}`,
  `http://m.lguplus.com${BALANCE}`,
  `https://m.lguplus.com:8443${BALANCE}`,
  'https://m.lguplus.com/uhdc/fo/myin/roam/v1/product-info',
  `https://m.lguplus.com${BALANCE}/`
]) {
  test(`unrelated or untrusted completed resources do not satisfy freshness: ${name}`, () => {
    assert.notEqual(read(mobileRow(), { entries: [completed({ name })] }).state, 'ready');
  });
}

test('ready results do not emit a script inventory', () => {
  const result = read(mobileRow() + '<script src="/_nuxt/app.js?token=fixture-only-token"></script>');
  assertReady(result);
  assert.deepEqual(result.assets, []);
});

test('diagnostic asset references are public first-party paths with no query or fragment', () => {
  const result = read('<script src="/static/uhdc-mobile/build/_nuxt/app.js?token=fixture-only-token#secret"></script>'
    + '<script src="https://www.lguplus.com/assets/my-roaming.js?private=fixture-only-identifier"></script>'
    + '<script src="https://m.lguplus.com/_next/chunk.js"></script>', { entries: [] });
  assert.deepEqual(result.assets, [
    'https://www.lguplus.com/assets/my-roaming.js',
    'https://m.lguplus.com/static/uhdc-mobile/build/_nuxt/app.js',
    'https://m.lguplus.com/_next/chunk.js'
  ]);
  assert.doesNotMatch(JSON.stringify(result), /fixture-only|[?#]/);
});

test('foreign, inline hydration, credentials, nonstatic and encoded asset paths are excluded', () => {
  const result = read('<script>window.__NUXT__={password:"fixture-only-secret"}</script>'
    + '<script src="https://analytics.invalid/static/app.js?token=fixture-only-token"></script>'
    + '<script src="http://m.lguplus.com/static/insecure.js"></script>'
    + '<script src="https://user:fixture-only-secret@m.lguplus.com/static/credentials.js"></script>'
    + '<script src="/account/customer.js"></script>'
    + '<script src="/_nuxt/encoded%2fsecret.js"></script>'
    + '<input type="password" value="fixture-only-secret">', { entries: [] });
  assert.deepEqual(result.assets, []);
  assert.doesNotMatch(JSON.stringify(result), /fixture-only|password|__NUXT__|customer/);
});

test('asset inventory is deduplicated and bounded', () => {
  const scripts = Array.from({ length: 20 }, (_, i) => `<script src="/_nuxt/chunk-${i}.js?private=fixture-only-secret"></script>`).join('')
    + '<script src="/_nuxt/chunk-0.js#another"></script>';
  const result = read(scripts, { entries: [] });
  assert.equal(result.assets.length, 12);
  assert.equal(new Set(result.assets).size, 12);
  assert.doesNotMatch(JSON.stringify(result), /fixture-only|[?#]/);
});

test('oversized DOM fails safely rather than scanning arbitrary page data indefinitely', () => {
  const result = read('<main>' + '<div></div>'.repeat(6001) + mobileRow() + '</main>');
  assert.equal(result.state, 'unsupported');
  assert.equal(result.stage, 'node_limit');
});
