const test = require('node:test');
const assert = require('node:assert/strict');
const { parseExpense, amountToCents, periodBounds, xmlTag } = require('./logic');

test('公众号简短支出消息解析，保留可选备注', () => {
  assert.deepEqual(parseExpense('午饭 20元'), { purpose: '午饭', amountCents: 2000, note: '' });
  assert.deepEqual(parseExpense('地铁 3.50元 回家'), { purpose: '地铁', amountCents: 350, note: '回家' });
  assert.equal(parseExpense('随便聊聊'), null);
  assert.equal(parseExpense('午饭 0元'), null);
  assert.equal(parseExpense('午饭 20.999元'), null);
});

test('金额使用整数分，周期边界正确', () => {
  assert.equal(amountToCents('20.05'), 2005);
  assert.deepEqual(periodBounds('month', '2026-12-27'), ['2026-12-01', '2027-01-01']);
  assert.deepEqual(periodBounds('day', '2026-12-31'), ['2026-12-31', '2027-01-01']);
  assert.throws(() => periodBounds('day', '2026-02-29'));
});

test('微信 CDATA 文本可以提取', () => {
  assert.equal(xmlTag('<xml><Content><![CDATA[午饭 20元]]></Content></xml>', 'Content'), '午饭 20元');
});
