const { createHash, createHmac, createDecipheriv, timingSafeEqual } = require('node:crypto');

function amountToCents(value) {
  const text = String(value).trim();
  if (!/^(?:0|[1-9]\d{0,8})(?:\.\d{1,2})?$/.test(text)) throw new Error('金额格式不正确');
  const [yuan, fraction = ''] = text.split('.');
  const cents = Number(yuan) * 100 + Number(fraction.padEnd(2, '0'));
  if (cents <= 0) throw new Error('金额必须大于 0');
  return cents;
}

function parseExpense(text) {
  const match = String(text).trim().match(/^(.{1,40}?)\s+([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元)?(?:\s+(.{1,200}))?$/u);
  if (!match) return null;
  try {
    return { purpose: match[1].trim(), amountCents: amountToCents(match[2]), note: (match[3] || '').trim() };
  } catch { return null; }
}

function signature(parts) {
  return createHash('sha1').update(parts.sort().join('')).digest('hex');
}

function validSignature(parts, received) {
  if (!/^[a-f0-9]{40}$/i.test(received || '')) return false;
  return timingSafeEqual(Buffer.from(signature(parts), 'hex'), Buffer.from(received, 'hex'));
}

function xmlTag(xml, name) {
  const match = xml.match(new RegExp(`<${name}>(?:<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>|([^<]*))<\\/${name}>`));
  if (!match) return '';
  return match[1] !== undefined ? match[1] : match[2].replace(/&(?:lt|gt|amp|quot|apos);/g, value => ({ '&lt;': '<', '&gt;': '>', '&amp;': '&', '&quot;': '"', '&apos;': "'" })[value]);
}

function decryptWechat(encrypted, encodingKey, appId) {
  const key = Buffer.from(`${encodingKey}=`, 'base64');
  if (key.length !== 32) throw new Error('微信 EncodingAESKey 不正确');
  const decipher = createDecipheriv('aes-256-cbc', key, key.subarray(0, 16));
  const buffer = Buffer.concat([decipher.update(Buffer.from(encrypted, 'base64')), decipher.final()]);
  const length = buffer.readUInt32BE(16);
  if (length < 0 || length > buffer.length - 20) throw new Error('微信消息长度不正确');
  const actualAppId = buffer.subarray(20 + length).toString('utf8');
  if (actualAppId !== appId) throw new Error('微信消息 AppID 不匹配');
  return buffer.subarray(20, 20 + length).toString('utf8');
}

function periodBounds(period, date) {
  if (!['day', 'month', 'year'].includes(period) || !/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('日期或统计周期不正确');
  const [y, m, d] = date.split('-').map(Number);
  const check = new Date(Date.UTC(y, m - 1, d));
  if (check.getUTCFullYear() !== y || check.getUTCMonth() !== m - 1 || check.getUTCDate() !== d) throw new Error('日期不存在');
  const start = period === 'year' ? `${y}-01-01` : period === 'month' ? `${y}-${String(m).padStart(2, '0')}-01` : date;
  const endDate = period === 'year' ? new Date(Date.UTC(y + 1, 0, 1)) : period === 'month' ? new Date(Date.UTC(y, m, 1)) : new Date(Date.UTC(y, m - 1, d + 1));
  return [start, endDate.toISOString().slice(0, 10)];
}

function bindingDigest(code, secret) {
  return createHmac('sha256', secret).update(code.toUpperCase()).digest('hex');
}

module.exports = { amountToCents, parseExpense, signature, validSignature, xmlTag, decryptWechat, periodBounds, bindingDigest };
