const http = require('node:http');
const { randomBytes, createHash } = require('node:crypto');
const { resolve } = require('node:path');
const { openStore } = require('./store');
const { amountToCents, parseExpense, validSignature, xmlTag, decryptWechat, periodBounds, bindingDigest } = require('./logic');

const config = {
  port: Number(process.env.PORT || 3000),
  dbFile: resolve(process.env.DB_FILE || './data/book-keeping.sqlite'),
  miniAppId: process.env.MINI_APP_ID || '', miniSecret: process.env.MINI_APP_SECRET || '',
  officialAppId: process.env.OFFICIAL_APP_ID || '', officialSecret: process.env.OFFICIAL_APP_SECRET || '',
  token: process.env.WECHAT_TOKEN || '', aesKey: process.env.WECHAT_ENCODING_AES_KEY || '',
  bindSecret: process.env.BIND_SECRET || '', fake: process.env.DEV_FAKE_WECHAT === '1'
};
if (!config.fake && (!config.miniAppId || !config.miniSecret || !config.officialAppId || !config.token || !config.bindSecret)) {
  console.error('缺少微信配置；请参照 server/.env.example 设置环境变量');
  process.exit(1);
}
if (config.fake) {
  config.miniAppId ||= 'dev-mini'; config.officialAppId ||= 'dev-official';
  config.token ||= 'dev-token'; config.bindSecret ||= 'dev-secret-only';
}
const db = openStore(config.dbFile);
const nowShanghai = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'medium', hour12: false }).format(new Date()).replace(' ', ' ');
const sha256 = value => createHash('sha256').update(value).digest('hex');
const json = (res, status, data) => { res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(data)); };
const fail = (res, status, message) => json(res, status, { error: message });

async function body(req) {
  let total = 0; const chunks = [];
  for await (const chunk of req) {
    total += chunk.length;
    if (total > 65536) throw new Error('请求内容过大');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

async function wechatJson(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(1800) });
  const data = await response.json();
  if (!response.ok || data.errcode) throw new Error(`微信接口错误：${data.errcode || response.status}`);
  return data;
}

async function miniIdentity(code) {
  if (config.fake) return { openid: `dev-${code}`, unionid: null };
  return wechatJson(`https://api.weixin.qq.com/sns/jscode2session?appid=${encodeURIComponent(config.miniAppId)}&secret=${encodeURIComponent(config.miniSecret)}&js_code=${encodeURIComponent(code)}&grant_type=authorization_code`);
}

let accessToken = null;
async function officialUnionId(openId) {
  if (config.fake || !config.officialSecret) return null;
  if (!accessToken || accessToken.expires < Date.now()) {
    const result = await wechatJson(`https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=${encodeURIComponent(config.officialAppId)}&secret=${encodeURIComponent(config.officialSecret)}`);
    accessToken = { value: result.access_token, expires: Date.now() + Math.max(60, result.expires_in - 300) * 1000 };
  }
  const info = await wechatJson(`https://api.weixin.qq.com/cgi-bin/user/info?access_token=${encodeURIComponent(accessToken.value)}&openid=${encodeURIComponent(openId)}&lang=zh_CN`);
  return info.unionid || null;
}

function mergeUsers(sourceId, targetId) {
  if (sourceId === targetId) return;
  const source = db.prepare('SELECT union_id FROM users WHERE id = ?').get(sourceId);
  const target = db.prepare('SELECT union_id FROM users WHERE id = ?').get(targetId);
  if (!source || !target || (source.union_id && target.union_id && source.union_id !== target.union_id)) throw new Error('微信身份关联冲突');
  if (source.union_id && !target.union_id) {
    db.prepare('UPDATE users SET union_id = NULL WHERE id = ?').run(sourceId);
    db.prepare('UPDATE users SET union_id = ? WHERE id = ?').run(source.union_id, targetId);
  }
  for (const table of ['identities', 'sessions', 'binding_codes', 'official_messages', 'records']) {
    db.prepare(`UPDATE ${table} SET user_id = ? WHERE user_id = ?`).run(targetId, sourceId);
  }
  db.prepare('DELETE FROM users WHERE id = ?').run(sourceId);
}

function linkIdentity(appId, openId, unionId) {
  const known = db.prepare('SELECT user_id FROM identities WHERE app_id = ? AND open_id = ?').get(appId, openId);
  if (known) {
    if (!unionId) return known.user_id;
    const oldUnion = db.prepare('SELECT union_id FROM users WHERE id = ?').get(known.user_id)?.union_id;
    if (oldUnion && oldUnion !== unionId) throw new Error('微信身份关联冲突');
    const sameUnion = db.prepare('SELECT id FROM users WHERE union_id = ?').get(unionId);
    if (sameUnion && sameUnion.id !== known.user_id) {
      db.exec('BEGIN IMMEDIATE');
      try {
        mergeUsers(known.user_id, sameUnion.id);
        db.exec('COMMIT');
        return sameUnion.id;
      } catch (error) { db.exec('ROLLBACK'); throw error; }
    }
    db.prepare('UPDATE users SET union_id = COALESCE(union_id, ?) WHERE id = ?').run(unionId, known.user_id);
    return known.user_id;
  }
  let user = unionId ? db.prepare('SELECT id FROM users WHERE union_id = ?').get(unionId) : null;
  if (!user) user = { id: db.prepare('INSERT INTO users(union_id) VALUES (?)').run(unionId || null).lastInsertRowid };
  db.prepare('INSERT INTO identities(app_id, open_id, user_id) VALUES (?, ?, ?)').run(appId, openId, user.id);
  return Number(user.id);
}

function userFromRequest(req) {
  const token = /^Bearer ([a-f0-9]{64})$/i.exec(req.headers.authorization || '')?.[1];
  if (!token) return null;
  const row = db.prepare('SELECT user_id FROM sessions WHERE token_hash = ? AND expires_at > ?').get(sha256(token), Date.now());
  return row?.user_id || null;
}

function postMessage(appId, msgId, openId, content, unionId) {
  const existing = db.prepare('SELECT status FROM official_messages WHERE app_id = ? AND msg_id = ?').get(appId, msgId);
  if (existing) return existing.status;
  let userId = db.prepare('SELECT user_id FROM identities WHERE app_id = ? AND open_id = ?').get(appId, openId)?.user_id;
  if (unionId) userId = linkIdentity(appId, openId, unionId);
  const bind = /^绑定\s+([A-Z0-9]{8})$/i.exec(content.trim());
  if (bind) {
    const digest = bindingDigest(bind[1], config.bindSecret);
    const code = db.prepare('SELECT user_id FROM binding_codes WHERE digest = ? AND expires_at > ? AND consumed_at IS NULL').get(digest, Date.now());
    if (code) {
      db.exec('BEGIN IMMEDIATE');
      try {
        const old = db.prepare('SELECT user_id FROM identities WHERE app_id = ? AND open_id = ?').get(appId, openId);
        if (old && old.user_id !== code.user_id) mergeUsers(old.user_id, code.user_id);
        if (!old) db.prepare('INSERT INTO identities(app_id, open_id, user_id) VALUES (?, ?, ?)').run(appId, openId, code.user_id);
        db.prepare('UPDATE binding_codes SET consumed_at = ? WHERE digest = ?').run(Date.now(), digest);
        db.prepare('UPDATE official_messages SET user_id = ? WHERE app_id = ? AND open_id = ? AND user_id IS NULL').run(code.user_id, appId, openId);
        db.exec('COMMIT'); userId = code.user_id;
      } catch (error) { db.exec('ROLLBACK'); throw error; }
    }
  }
  const parsed = !bind && parseExpense(content);
  const status = bind ? (userId ? 'BOUND' : 'NEEDS_BINDING') : !userId ? 'NEEDS_BINDING' : parsed ? 'POSTED' : 'NEEDS_REVIEW';
  db.exec('BEGIN IMMEDIATE');
  try {
    db.prepare('INSERT INTO official_messages(app_id,msg_id,open_id,content,status,user_id,received_at) VALUES (?,?,?,?,?,?,?)').run(appId, msgId, openId, content, status, userId || null, nowShanghai());
    if (parsed && userId) db.prepare('INSERT INTO records(user_id,amount_cents,purpose,note,occurred_at,source,message_app_id,message_id) VALUES (?,?,?,?,?,?,?,?)').run(userId, parsed.amountCents, parsed.purpose, parsed.note, nowShanghai(), 'OFFICIAL_ACCOUNT', appId, msgId);
    if (userId) {
      const pending = db.prepare("SELECT msg_id, content, received_at FROM official_messages WHERE app_id = ? AND open_id = ? AND status = 'NEEDS_BINDING' ORDER BY received_at").all(appId, openId);
      for (const item of pending) {
        const expense = parseExpense(item.content);
        db.prepare('UPDATE official_messages SET status = ?, user_id = ? WHERE app_id = ? AND msg_id = ?').run(expense ? 'POSTED' : 'NEEDS_REVIEW', userId, appId, item.msg_id);
        if (expense) db.prepare('INSERT OR IGNORE INTO records(user_id,amount_cents,purpose,note,occurred_at,source,message_app_id,message_id) VALUES (?,?,?,?,?,?,?,?)').run(userId, expense.amountCents, expense.purpose, expense.note, item.received_at, 'OFFICIAL_ACCOUNT', appId, item.msg_id);
      }
    }
    db.exec('COMMIT'); return status;
  } catch (error) { db.exec('ROLLBACK'); throw error; }
}

async function handler(req, res) {
  const url = new URL(req.url, 'http://localhost');
  try {
    if (url.pathname === '/health') return json(res, 200, { ok: true });
    if (url.pathname === '/wechat/callback') {
      const timestamp = url.searchParams.get('timestamp') || '';
      const nonce = url.searchParams.get('nonce') || '';
      if (req.method === 'GET') {
        if (!validSignature([config.token, timestamp, nonce], url.searchParams.get('signature'))) return fail(res, 403, '微信签名错误');
        res.writeHead(200, { 'content-type': 'text/plain' }); return res.end(url.searchParams.get('echostr') || '');
      }
      if (req.method !== 'POST') return fail(res, 405, '方法不支持');
      let xml = await body(req);
      const encrypted = xmlTag(xml, 'Encrypt');
      if (encrypted) {
        if (!config.aesKey || !validSignature([config.token, timestamp, nonce, encrypted], url.searchParams.get('msg_signature'))) return fail(res, 403, '微信消息签名错误');
        xml = decryptWechat(encrypted, config.aesKey, config.officialAppId);
      } else if (!validSignature([config.token, timestamp, nonce], url.searchParams.get('signature'))) return fail(res, 403, '微信消息签名错误');
      if (xmlTag(xml, 'MsgType') === 'text') {
        const openId = xmlTag(xml, 'FromUserName'), msgId = xmlTag(xml, 'MsgId'), content = xmlTag(xml, 'Content');
        if (!openId || !/^\d+$/.test(msgId) || !content) return fail(res, 400, '消息格式不正确');
        let unionId = null;
        try { unionId = await officialUnionId(openId); } catch (error) { console.error(error.message); }
        postMessage(config.officialAppId, msgId, openId, content, unionId);
      }
      res.writeHead(200, { 'content-type': 'text/plain' }); return res.end('success');
    }
    if (req.method === 'POST' && url.pathname === '/api/session') {
      const input = JSON.parse(await body(req));
      if (typeof input.code !== 'string' || input.code.length < 2 || input.code.length > 200) return fail(res, 400, '缺少微信登录凭证');
      const identity = await miniIdentity(input.code);
      if (!identity.openid) return fail(res, 502, '微信没有返回用户标识');
      const userId = linkIdentity(config.miniAppId, identity.openid, identity.unionid);
      const token = randomBytes(32).toString('hex');
      db.prepare('INSERT INTO sessions(token_hash,user_id,expires_at) VALUES (?,?,?)').run(sha256(token), userId, Date.now() + 30 * 86400000);
      return json(res, 200, { token });
    }
    const userId = userFromRequest(req);
    if (!userId) return fail(res, 401, '请重新打开小程序');
    if (req.method === 'GET' && url.pathname === '/api/me') {
      const user = db.prepare('SELECT display_name FROM users WHERE id = ?').get(userId);
      const bound = !!db.prepare('SELECT 1 FROM identities WHERE user_id = ? AND app_id = ?').get(userId, config.officialAppId);
      return json(res, 200, { displayName: user.display_name || '记账用户', officialBound: bound });
    }
    if (req.method === 'POST' && url.pathname === '/api/me') {
      const input = JSON.parse(await body(req));
      const name = String(input.displayName || '').trim();
      if (name.length > 30) return fail(res, 400, '昵称不能超过 30 字');
      db.prepare('UPDATE users SET display_name = ? WHERE id = ?').run(name || null, userId);
      return json(res, 200, { displayName: name || '记账用户' });
    }
    if (req.method === 'POST' && url.pathname === '/api/binding-code') {
      const code = randomBytes(5).toString('hex').slice(0, 8).toUpperCase();
      const expiresAt = Date.now() + 10 * 60000;
      db.prepare('INSERT INTO binding_codes(digest,user_id,expires_at) VALUES (?,?,?)').run(bindingDigest(code, config.bindSecret), userId, expiresAt);
      return json(res, 200, { code, expiresAt });
    }
    if (req.method === 'GET' && url.pathname === '/api/records') {
      const period = url.searchParams.get('period') || 'month';
      const date = url.searchParams.get('date') || nowShanghai().slice(0, 10);
      const [start, end] = periodBounds(period, date);
      const records = db.prepare('SELECT id,amount_cents,purpose,note,occurred_at,source FROM records WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC,id DESC').all(userId, start, end);
      const total = db.prepare('SELECT COALESCE(SUM(amount_cents),0) AS total FROM records WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?').get(userId, start, end).total;
      return json(res, 200, { period, date, totalCents: total, records: records.map(recordOutput) });
    }
    const detail = /^\/api\/records\/(\d+)$/.exec(url.pathname);
    if (req.method === 'GET' && detail) {
      const record = db.prepare('SELECT id,amount_cents,purpose,note,occurred_at,source FROM records WHERE id = ? AND user_id = ?').get(Number(detail[1]), userId);
      return record ? json(res, 200, recordOutput(record)) : fail(res, 404, '账单不存在');
    }
    if (req.method === 'POST' && url.pathname === '/api/records') {
      const input = JSON.parse(await body(req));
      const purpose = String(input.purpose || '').trim(), note = String(input.note || '').trim();
      if (!purpose || purpose.length > 40 || note.length > 200) return fail(res, 400, '用途或备注长度不正确');
      const cents = amountToCents(input.amount);
      const occurredAt = input.occurredAt || nowShanghai();
      if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(occurredAt)) return fail(res, 400, '时间格式不正确');
      periodBounds('day', occurredAt.slice(0, 10));
      const id = db.prepare('INSERT INTO records(user_id,amount_cents,purpose,note,occurred_at,source) VALUES (?,?,?,?,?,?)').run(userId, cents, purpose, note, occurredAt, 'MINI_PROGRAM').lastInsertRowid;
      return json(res, 201, { id: Number(id) });
    }
    return fail(res, 404, '接口不存在');
  } catch (error) {
    console.error(error);
    const clientError = error instanceof SyntaxError || ['金额格式不正确', '金额必须大于 0', '日期或统计周期不正确', '日期不存在', '请求内容过大'].includes(error.message);
    return fail(res, clientError ? 400 : 500, clientError ? error.message : '请求处理失败');
  }
}

function recordOutput(row) {
  return { id: row.id, amount: (row.amount_cents / 100).toFixed(2), purpose: row.purpose, note: row.note, occurredAt: row.occurred_at, source: row.source };
}

if (require.main === module) http.createServer(handler).listen(config.port, () => console.log(`API listening on ${config.port}`));
module.exports = { handler, db, config, postMessage };
