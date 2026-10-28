<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, query } from './api'

const tab = ref('budgets')
const month = ref(new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 7))
const budgets = ref([]), reminders = ref([]), recurring = ref([]), pending = ref([]), notifications = ref([])
const categories = ref([]), recurringCategories = ref([])
const budgetLedgers = ref([]), templateLedgers = ref([])
const busy = ref(false), message = ref(''), error = ref('')
const budget = reactive({ userId: '', bookId: '', month: month.value, categoryId: '', amount: '' })
const reminder = reactive({ userId: '', kind: 'BOOKKEEPING', title: '', dueAt: '', recurrence: 'DAILY', leadDays: 0 })
const template = reactive({ userId: '', bookId: '', type: 'EXPENSE', amount: '', purpose: '', categoryId: '', note: '', dayOfMonth: 1, startMonth: month.value, endMonth: '' })
const money = value => `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const percent = row => Number(row.amount) ? Math.round(Number(row.spent) / Number(row.amount) * 100) : 0
const alertCount = computed(() => budgets.value.filter(row => percent(row) > 100).length)
const label = status => ({ PENDING: '待发送', SENDING: '发送中', SENT: '已发送', NO_PERMISSION: '无授权', FAILED: '失败' })[status] || status

function show(text, failed = false) { message.value = failed ? '' : text; error.value = failed ? text : '' }
async function load() {
  busy.value = true
  try {
    const [b, r, t, p, n] = await Promise.all([
      api(`/budgets?${query({ month: month.value })}`), api('/reminders'), api('/recurring-bills'), api('/pending-bills'), api('/notifications')
    ])
    budgets.value = b; reminders.value = r; recurring.value = t; pending.value = p; notifications.value = n
  } catch (e) { show(e.message, true) }
  finally { busy.value = false }
}
onMounted(load)
watch(month, () => { budget.month = month.value; load() })
watch(() => budget.userId, async id => {
  budgetLedgers.value = []; budget.bookId = ''; categories.value = []
  if (id) try { budgetLedgers.value = await api(`/ledgers?${query({ userId: id })}`) } catch (e) { show(e.message, true) }
})
watch(() => budget.bookId, async () => {
  categories.value = []; budget.categoryId = ''
  if (budget.userId && budget.bookId) try { categories.value = await api(`/categories?${query({ userId: budget.userId, bookId: budget.bookId, type: 'EXPENSE' })}`) } catch (e) { show(e.message, true) }
})
watch(() => template.userId, async id => {
  templateLedgers.value = []; template.bookId = ''; recurringCategories.value = []
  if (id) try { templateLedgers.value = await api(`/ledgers?${query({ userId: id })}`) } catch (e) { show(e.message, true) }
})
watch(() => [template.bookId, template.type], async () => {
  recurringCategories.value = []; template.categoryId = ''
  if (template.userId && template.bookId) try { recurringCategories.value = await api(`/categories?${query({ userId: template.userId, bookId: template.bookId, type: template.type })}`) } catch (e) { show(e.message, true) }
})
async function act(path, method = 'POST', body = null, success = '已保存') {
  busy.value = true
  try {
    await api(path, { method, ...(body === null ? {} : { body: JSON.stringify(body) }) })
    show(success); await load()
  } catch (e) { show(e.message, true) }
  finally { busy.value = false }
}
function saveBudget() { act('/budgets', 'POST', budget, '预算已保存') }
function removeBudget(row) { if (confirm(`删除 ${row.userName} 的「${row.category}」预算？`)) act(`/budgets/${row.id}`, 'DELETE', null, '预算已删除') }
function saveReminder() {
  act('/reminders', 'POST', { ...reminder, dueAt: reminder.dueAt.replace('T', ' ') + ':00' }, '提醒已创建')
}
function saveRecurring() { act('/recurring-bills', 'POST', template, '周期账单模板已创建') }
function decide(row, approve) { if (confirm(`${approve ? '确认入账' : '忽略'}「${row.purpose}」？`)) act(`/pending-bills/${row.id}/${approve ? 'approve' : 'dismiss'}`, 'POST', null, approve ? '已入账' : '已忽略') }
</script>

<template>
  <div class="planning">
    <div v-if="message || error" class="planning-message" :class="{ bad: error }">{{ error || message }}</div>
    <div class="planning-tabs">
      <button v-for="item in [{ id: 'budgets', text: '预算与超支' }, { id: 'reminders', text: '记账与到期提醒' }, { id: 'recurring', text: '周期账单' }, { id: 'delivery', text: '消息记录' }]" :key="item.id" :class="{ selected: tab === item.id }" @click="tab = item.id">{{ item.text }}<b v-if="item.id === 'recurring' && pending.length">{{ pending.length }}</b></button>
    </div>

    <template v-if="tab === 'budgets'">
      <div class="planning-summary"><div><span>预算月份</span><input v-model="month" type="month" /></div><div class="planning-alert"><span>超支预算</span><strong>{{ alertCount }}</strong><small>项需要关注</small></div><div class="planning-alert"><span>已设置预算</span><strong>{{ budgets.length }}</strong><small>项正在跟踪</small></div></div>
      <div class="planning-columns"><section class="panel planning-panel"><h2>预算执行</h2><p class="planning-hint">月度总预算与分类预算按实际支出实时计算</p><div v-if="!budgets.length" class="planning-empty">该月还没有预算，使用右侧表单创建。</div><div v-for="row in budgets" :key="row.id" class="budget-item"><div class="budget-line"><span><b>{{ row.userName }}</b> <small>#{{ row.userId }} · {{ row.category }}</small></span><button class="link-button danger" @click="removeBudget(row)">删除</button></div><div class="budget-values"><strong>{{ money(row.spent) }}</strong><span>/ {{ money(row.amount) }}</span><b :class="{ over: percent(row) > 100 }">{{ percent(row) }}%</b></div><div class="budget-progress"><i :class="{ over: percent(row) > 100 }" :style="{ width: `${Math.min(100, percent(row))}%` }"></i></div><p v-if="percent(row) > 100" class="overspend">已超支 {{ money(Number(row.spent) - Number(row.amount)) }}</p></div></section>
      <form class="panel planning-panel planning-form" @submit.prevent="saveBudget"><h2>设置预算</h2><p class="planning-hint">同一账本、月份和分类再次保存会更新金额。</p><label>用户 ID<input v-model="budget.userId" type="number" min="1" required placeholder="从用户中心查找" /></label><label>账本<select v-model="budget.bookId" required><option value="">请选择账本</option><option v-for="book in budgetLedgers" :key="book.id" :value="book.id">{{ book.name }} #{{ book.id }}</option></select></label><label>月份<input v-model="budget.month" type="month" required /></label><label>预算范围<select v-model="budget.categoryId"><option value="">月度总预算</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label><label>预算金额<input v-model="budget.amount" type="number" min="0.01" step="0.01" required placeholder="0.00" /></label><button class="primary" :disabled="busy">保存预算</button></form></div>
    </template>

    <template v-if="tab === 'reminders'">
      <div class="planning-columns"><section class="panel planning-panel"><h2>提醒规则</h2><p class="planning-hint">微信订阅消息需要用户在小程序「我的」页面主动授权。</p><div v-if="!reminders.length" class="planning-empty">尚未设置提醒。</div><div v-for="row in reminders" :key="row.id" class="rule-row"><div><strong>{{ row.title }}</strong><p>{{ row.userName }} #{{ row.userId }} · {{ row.kind === 'BOOKKEEPING' ? '记账提醒' : '账单到期' }} · {{ row.recurrence === 'ONCE' ? '仅一次' : row.recurrence === 'DAILY' ? '每天' : '每月' }}</p><small>{{ row.dueAt }}{{ row.leadDays ? ` · 提前 ${row.leadDays} 天` : '' }}</small></div><div><button class="link-button" @click="act(`/reminders/${row.id}`, 'PATCH', { active: !row.active }, row.active ? '提醒已暂停' : '提醒已启用')">{{ row.active ? '暂停' : '启用' }}</button><button class="link-button danger" @click="confirm('确定删除提醒？') && act(`/reminders/${row.id}`, 'DELETE', null, '提醒已删除')">删除</button></div></div></section>
      <form class="panel planning-panel planning-form" @submit.prevent="saveReminder"><h2>新增提醒</h2><p class="planning-hint">可用于每日记账、房租和信用卡还款。</p><label>用户 ID<input v-model="reminder.userId" type="number" min="1" required /></label><label>提醒类型<select v-model="reminder.kind"><option value="BOOKKEEPING">记账提醒</option><option value="DUE">账单到期提醒</option></select></label><label>标题<input v-model="reminder.title" maxlength="80" required placeholder="例如 信用卡还款" /></label><label>首次到期时间<input v-model="reminder.dueAt" type="datetime-local" required /></label><label>重复方式<select v-model="reminder.recurrence"><option value="ONCE">仅一次</option><option value="DAILY">每天</option><option value="MONTHLY">每月</option></select></label><label>提前天数<input v-model="reminder.leadDays" type="number" min="0" max="30" required /></label><button class="primary" :disabled="busy">创建提醒</button></form></div>
    </template>

    <template v-if="tab === 'recurring'">
      <section class="panel planning-panel pending-panel"><h2>待确认账单 <span class="count-pill">{{ pending.length }}</span></h2><p class="planning-hint">系统按月生成，确认后才计入用户账本。</p><div v-if="!pending.length" class="planning-empty">当前没有待确认账单。</div><div v-for="row in pending" :key="row.id" class="rule-row"><div><strong>{{ row.purpose }} <span :class="row.type === 'INCOME' ? 'green-text' : ''">{{ money(row.amount) }}</span></strong><p>{{ row.userName }} #{{ row.userId }} · 计划日期 {{ row.scheduledOn }}</p></div><div><button class="link-button" @click="decide(row, true)">确认入账</button><button class="link-button danger" @click="decide(row, false)">忽略</button></div></div></section>
      <div class="planning-columns"><section class="panel planning-panel"><h2>周期账单模板</h2><p class="planning-hint">每月 29–31 日在短月份顺延为该月最后一天。</p><div v-if="!recurring.length" class="planning-empty">尚未设置周期账单。</div><div v-for="row in recurring" :key="row.id" class="rule-row"><div><strong>{{ row.purpose }} · {{ money(row.amount) }}</strong><p>{{ row.userName }} #{{ row.userId }} · 每月 {{ row.dayOfMonth }} 日 · {{ row.category }}</p><small>{{ row.startMonth }} 起{{ row.endMonth ? ` 至 ${row.endMonth}` : '' }}</small></div><button class="link-button" @click="act(`/recurring-bills/${row.id}`, 'PATCH', { active: !row.active }, row.active ? '模板已暂停' : '模板已启用')">{{ row.active ? '暂停' : '启用' }}</button></div></section>
      <form class="panel planning-panel planning-form" @submit.prevent="saveRecurring"><h2>新增周期账单</h2><p class="planning-hint">例如房租、工资或订阅费用。</p><label>用户 ID<input v-model="template.userId" type="number" min="1" required /></label><label>账本<select v-model="template.bookId" required><option value="">请选择账本</option><option v-for="book in templateLedgers" :key="book.id" :value="book.id">{{ book.name }} #{{ book.id }}</option></select></label><label>收支类型<select v-model="template.type"><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select></label><label>用途<input v-model="template.purpose" maxlength="40" required /></label><label>金额<input v-model="template.amount" type="number" min="0.01" step="0.01" required /></label><label>分类<select v-model="template.categoryId"><option value="">默认：其他</option><option v-for="c in recurringCategories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label><label>每月生成日<input v-model="template.dayOfMonth" type="number" min="1" max="31" required /></label><label>开始月份<input v-model="template.startMonth" type="month" required /></label><label>结束月份（可选）<input v-model="template.endMonth" type="month" /></label><label>备注<input v-model="template.note" maxlength="500" /></label><button class="primary" :disabled="busy">创建模板</button></form></div>
    </template>

    <template v-if="tab === 'delivery'"><section class="panel planning-panel"><h2>订阅消息记录</h2><p class="planning-hint">显示最近 50 条预算和提醒消息。没有模板或用户授权时不会发送。</p><div v-if="!notifications.length" class="planning-empty">还没有消息记录。</div><div v-for="row in notifications" :key="row.id" class="rule-row"><div><strong>{{ row.title }}</strong><p>{{ row.userName }} #{{ row.userId }} · {{ row.content }}</p><small>{{ row.triggerAt }}{{ row.error ? ` · ${row.error}` : '' }}</small></div><span class="status" :class="row.status === 'SENT' ? 'good' : row.status === 'FAILED' ? 'disabled' : ''">{{ label(row.status) }}</span></div></section></template>
  </div>
</template>

<style scoped>
.planning-tabs{display:flex;gap:5px;padding:5px;background:#eaf3e9;border-radius:11px;width:max-content;max-width:100%;overflow:auto;margin-bottom:19px}.planning-tabs button{border:0;background:transparent;color:#719078;padding:10px 15px;border-radius:8px;font-size:12px;font-weight:700;white-space:nowrap}.planning-tabs button.selected{background:#fff;color:#428257;box-shadow:0 3px 10px #58795b18}.planning-tabs b,.count-pill{background:#dceee0;color:#4b9761;border-radius:20px;padding:2px 6px;font-size:10px;margin-left:5px}.planning-summary{display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:15px;margin-bottom:17px}.planning-summary>div{background:#fff;border:1px solid #e9f0e8;border-radius:13px;padding:17px 20px;min-height:100px}.planning-summary span{display:block;color:#8ea292;font-size:12px}.planning-summary input{margin-top:13px}.planning-alert strong{display:inline-block;font-size:29px;margin:10px 6px 0 0;color:#467c54}.planning-alert small{color:#a0afa2}.planning-columns{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(280px,.9fr);gap:17px;align-items:start}.planning-panel{padding:23px;margin-bottom:17px}.planning-hint{font-size:11px;color:#a0afa3;margin-top:7px;margin-bottom:21px}.budget-item{border-top:1px solid #eef3ed;padding:17px 0 2px}.budget-line,.budget-values{display:flex;align-items:center;justify-content:space-between}.budget-line b{font-size:12px}.budget-line small{color:#9cac9e;margin-left:5px}.budget-values{margin-top:13px;gap:8px;justify-content:flex-start}.budget-values strong{font-size:17px}.budget-values span{color:#a7b5a8;font-size:11px}.budget-values b{margin-left:auto;color:#5a9b6e;font-size:11px}.budget-values b.over,.overspend{color:#d17e6f}.budget-progress{height:7px;background:#edf3ed;border-radius:20px;overflow:hidden;margin-top:10px}.budget-progress i{display:block;background:#80bc8d;height:100%;border-radius:20px}.budget-progress i.over{background:#d68d80}.overspend{font-size:11px;margin-top:7px}.planning-form{display:grid;gap:13px}.planning-form .planning-hint{margin-top:-7px;margin-bottom:5px}.planning-form label{color:#718576;font-size:11px;font-weight:700}.planning-form input,.planning-form select{display:block;width:100%;margin-top:6px}.planning-form .primary{margin-top:6px}.rule-row{display:flex;align-items:center;justify-content:space-between;gap:10px;border-top:1px solid #eff3ee;padding:16px 0}.rule-row strong{font-size:12px}.rule-row p{color:#8c9e8f;font-size:11px;margin-top:6px}.rule-row small{color:#a6b4a7;display:block;margin-top:6px}.rule-row>div:last-child{white-space:nowrap}.planning-empty{text-align:center;color:#a2b1a4;padding:46px 15px;font-size:12px}.pending-panel{margin-bottom:17px}.green-text{color:#5a9d6c}.planning-message{background:#edf7ed;color:#4f8d60;border-radius:8px;padding:10px 14px;font-size:12px;margin-bottom:15px}.planning-message.bad{background:#fceeed;color:#c57569}@media(max-width:900px){.planning-columns{grid-template-columns:1fr}}@media(max-width:650px){.planning-summary{grid-template-columns:1fr}.planning-tabs{width:100%}.rule-row{align-items:start;flex-direction:column}}
</style>
