const test = require('node:test');
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { mkdtempSync, rmSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { signature } = require('./logic');

test('公众号先发账单，绑定后在小程序显示且重复推送不重复入账', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'book-keeping-test-'));
  process.env.DB_FILE = join(dir, 'test.sqlite');
  process.env.DEV_FAKE_WECHAT = '1';
  const { handler, db, postMessage } = require('./index');
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const callback = async (msgId, content, openId = 'oa-user') => {
    const timestamp = '123', nonce = '456';
    const signed = signature(['dev-token', timestamp, nonce]);
    const xml = `<xml><FromUserName><![CDATA[${openId}]]></FromUserName><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[${content}]]></Content><MsgId>${msgId}</MsgId></xml>`;
    return fetch(`${base}/wechat/callback?timestamp=${timestamp}&nonce=${nonce}&signature=${signed}`, { method: 'POST', body: xml });
  };
  try {
    assert.equal((await callback('1001', '午饭 20元')).status, 200);
    const session = await (await fetch(`${base}/api/session`, { method: 'POST', body: JSON.stringify({ code: 'alice' }) })).json();
    const auth = { Authorization: `Bearer ${session.token}` };
    let ledger = await (await fetch(`${base}/api/records?period=year&date=2026-10-27`, { headers: auth })).json();
    assert.equal(ledger.records.length, 0);
    const { code } = await (await fetch(`${base}/api/binding-code`, { method: 'POST', headers: auth })).json();
    assert.equal((await callback('1002', `绑定 ${code}`)).status, 200);
    assert.equal((await callback('1001', '午饭 20元')).status, 200);
    ledger = await (await fetch(`${base}/api/records?period=year&date=2026-10-27`, { headers: auth })).json();
    assert.equal(ledger.records.length, 1);
    assert.equal(ledger.totalCents, 2000);
    assert.equal(ledger.records[0].purpose, '午饭');
    const added = await (await fetch(`${base}/api/records`, { method: 'POST', headers: auth, body: JSON.stringify({ amount: '3.50', purpose: '地铁', note: '回家' }) })).json();
    const detail = await (await fetch(`${base}/api/records/${added.id}`, { headers: auth })).json();
    assert.equal(detail.amount, '3.50');
    assert.equal(detail.note, '回家');
    const bob = await (await fetch(`${base}/api/session`, { method: 'POST', body: JSON.stringify({ code: 'bob' }) })).json();
    const other = await (await fetch(`${base}/api/records?period=year&date=2026-10-27`, { headers: { Authorization: `Bearer ${bob.token}` } })).json();
    assert.equal(other.records.length, 0);
    postMessage('dev-official', '2001', 'oa-charlie', '咖啡 11元', 'union-charlie');
    const charlie = await (await fetch(`${base}/api/session`, { method: 'POST', body: JSON.stringify({ code: 'charlie' }) })).json();
    const charlieAuth = { Authorization: `Bearer ${charlie.token}` };
    const secondCode = await (await fetch(`${base}/api/binding-code`, { method: 'POST', headers: charlieAuth })).json();
    assert.equal((await callback('2002', `绑定 ${secondCode.code}`, 'oa-charlie')).status, 200);
    const merged = await (await fetch(`${base}/api/records?period=year&date=2026-10-27`, { headers: charlieAuth })).json();
    assert.equal(merged.records[0].purpose, '咖啡');
  } finally {
    await new Promise(resolve => server.close(resolve));
    db.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
