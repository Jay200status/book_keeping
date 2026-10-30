<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import Chart from './Chart.vue'
import PlanningView from './PlanningView.vue'
import SystemView from './SystemView.vue'
import OperationsView from './OperationsView.vue'
import LedgerView from './LedgerView.vue'
import { api, clearToken, download, query, saveToken, token } from './api'

const section = ref('dashboard')
const loginForm = reactive({ username: '', password: '' })
const loggedIn = ref(Boolean(token()))
const admin = ref(null)
const loading = ref(false)
const notice = ref('')
const error = ref('')
const dashboard = ref(null)
const anomalies = ref([])
const users = ref({ items: [], total: 0, page: 1, size: 10 })
const bills = ref({ items: [], total: 0, page: 1, size: 10 })
const ledgerOptions = ref([])
const userLedgers = ref([])
const userFilter = reactive({ search: '', page: 1, size: 10 })
const billFilter = reactive({ search: '', type: '', from: '', to: '', bookId: '', page: 1, size: 10 })
const modal = ref(false)
const editingId = ref(null)
const form = reactive({ userId: '', bookId: '', type: 'EXPENSE', amount: '', purpose: '', categoryId: '', occurredAt: '', note: '' })
const categories = ref([])
const saving = ref(false)
const userLogins = ref([])
const loginHistoryUser = ref(null)

const nav = [
  { id: 'dashboard', icon: '◫', label: '数据看板', sub: 'Dashboard' },
  { id: 'users', icon: '♙', label: '用户中心', sub: 'User Center' },
  { id: 'ledgers', icon: '▧', label: '账本管理', sub: 'Ledger Management' },
  { id: 'bills', icon: '▤', label: '账单统计', sub: 'Bill Statistics' },
  { id: 'planning', icon: '◷', label: '预算与提醒', sub: 'Budget & Reminders' },
  { id: 'operations', icon: '✎', label: '运营管理', sub: 'Operations' },
  { id: 'system', icon: '⚙', label: '权限与系统', sub: 'System & Security' }
]
const visibleNav = computed(() => nav.filter(item => item.id === 'dashboard' || admin.value?.role === 'ADMIN'))
const current = computed(() => nav.find(item => item.id === section.value))
const money = value => `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const today = new Date().toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
const dateNow = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 16)
const dateLabel = value => value?.replace('T', ' ').slice(0, 16) || '—'
const sourceLabel = value => ({ MINI_PROGRAM: '小程序', OFFICIAL_ACCOUNT: '公众号', ADMIN: '管理端' })[value] || value
const pages = result => Math.max(1, Math.ceil(result.total / result.size))

function flash(message, isError = false) {
  if (isError) { error.value = message; notice.value = '' }
  else { notice.value = message; error.value = '' }
  window.setTimeout(() => { if (notice.value === message) notice.value = ''; if (error.value === message) error.value = '' }, 4500)
}
async function login() {
  loading.value = true
  try {
    const result = await api('/session', { method: 'POST', body: JSON.stringify(loginForm) })
    saveToken(result.token)
    admin.value = { username: result.username, role: result.role }
    loggedIn.value = true
    loginForm.password = ''
    await loadCurrent()
  } catch (e) { flash(e.message, true) }
  finally { loading.value = false }
}
async function logout() {
  try { await api('/session', { method: 'DELETE' }) } catch { /* local session is cleared below */ }
  clearToken(); loggedIn.value = false; admin.value = null; dashboard.value = null
}
async function restore() {
  if (!token()) return
  try { admin.value = await api('/session'); loggedIn.value = true; await loadCurrent() }
  catch { clearToken(); loggedIn.value = false }
}
function expired() { loggedIn.value = false; admin.value = null; flash('登录已过期，请重新登录', true) }
onMounted(() => { window.addEventListener('admin-session-expired', expired); restore() })
onUnmounted(() => window.removeEventListener('admin-session-expired', expired))

async function loadCurrent() {
  if (!loggedIn.value) return
  loading.value = true
  try {
    if (section.value === 'dashboard') {
      dashboard.value = await api('/dashboard')
      anomalies.value = admin.value?.role === 'ADMIN' ? await api('/anomalies') : []
    }
    if (section.value === 'users') users.value = await api(`/users?${query(userFilter)}`)
    if (section.value === 'bills') { ledgerOptions.value = await api('/ledgers'); bills.value = await api(`/bills?${query(billFilter)}`) }
  } catch (e) { flash(e.message, true) }
  finally { loading.value = false }
}
function navigate(id) { section.value = id; loadCurrent() }
function openLedgerBills(bookId) { billFilter.bookId = bookId; billFilter.page = 1; navigate('bills') }
function openAnomaly(row) { billFilter.bookId = row.bookId; billFilter.search = `#${row.id}`; billFilter.page = 1; navigate('bills') }
function searchUsers() { userFilter.page = 1; loadCurrent() }
function searchBills() { billFilter.page = 1; loadCurrent() }
async function exportBills() {
  try { await download(`/bills/export?${query({ search: billFilter.search, type: billFilter.type, from: billFilter.from, to: billFilter.to, bookId: billFilter.bookId })}`, 'bookkeeping-bills.csv') }
  catch (e) { flash(e.message, true) }
}
function changePage(target, delta) {
  const result = target === 'users' ? users.value : bills.value
  const filter = target === 'users' ? userFilter : billFilter
  filter.page = Math.max(1, Math.min(pages(result), filter.page + delta))
  loadCurrent()
}

const dailyOption = computed(() => {
  const rows = dashboard.value?.daily || []
  const values = Object.fromEntries(rows.map(row => [row.date, Number(row.amount)]))
  const dates = Array.from({ length: 7 }, (_, i) => {
    const time = new Date(Date.now() + 8 * 3600000 - (6 - i) * 86400000)
    return time.toISOString().slice(0, 10)
  })
  return {
    color: ['#64ae80'], tooltip: { trigger: 'axis', valueFormatter: value => money(value) },
    grid: { left: 54, right: 18, top: 18, bottom: 34 },
    xAxis: { type: 'category', data: dates.map(date => date.slice(5).replace('-', '/')), boundaryGap: false,
      axisLine: { lineStyle: { color: '#dce9de' } }, axisLabel: { color: '#829588' }, axisTick: { show: false } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf3ee' } }, axisLabel: { color: '#829588' } },
    series: [{ type: 'line', smooth: true, symbolSize: 8, data: dates.map(date => values[date] || 0),
      lineStyle: { width: 3 }, areaStyle: { color: 'rgba(103, 183, 132, .16)' } }]
  }
})
const purposeOption = computed(() => {
  const rows = dashboard.value?.purpose || []
  return {
    color: ['#6eaf83', '#9acba5', '#c2dec0', '#a8c9bc', '#7caa91', '#d4e6cb', '#92bda5', '#e3eddd'],
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${money(p.value)} · ${p.percent}%` },
    legend: { bottom: 0, textStyle: { color: '#75877a' }, type: 'scroll' },
    series: [{ type: 'pie', radius: ['46%', '72%'], center: ['50%', '43%'], label: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 3 }, data: rows.map(row => ({ name: row.name, value: Number(row.amount) })) }]
  }
})

async function saveUser(user, field, value) {
  try {
    await api(`/users/${user.id}`, { method: 'PATCH', body: JSON.stringify({ [field]: value }) })
    flash('用户信息已更新'); await loadCurrent()
  } catch (e) { flash(e.message, true) }
}
function renameUser(user) {
  const name = window.prompt('请输入用户昵称（最多 30 字）', user.displayName === '记账用户' ? '' : user.displayName)
  if (name !== null) saveUser(user, 'displayName', name)
}
function toggleUser(user) {
  const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (window.confirm(`${next === 'DISABLED' ? '停用' : '启用'}用户 #${user.id}？`)) saveUser(user, 'status', next)
}
async function openUserLogins(user) {
  try { userLogins.value = await api(`/users/${user.id}/logins`); loginHistoryUser.value = user }
  catch (e) { flash(e.message, true) }
}

function openAdd() {
  editingId.value = null
  Object.assign(form, { userId: '', bookId: '', type: 'EXPENSE', amount: '', purpose: '', categoryId: '', occurredAt: dateNow(), note: '' })
  categories.value = []; userLedgers.value = []; modal.value = true
}
async function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, { userId: item.userId, bookId: item.bookId, type: item.type, amount: item.amount, purpose: item.purpose,
    categoryId: item.categoryId, occurredAt: item.occurredAt.replace(' ', 'T').slice(0, 16), note: item.note })
  modal.value = true; await nextTick(); form.categoryId = item.categoryId; loadUserLedgers(); loadCategories()
}
async function loadCategories() {
  categories.value = []
  if (!form.userId || !form.bookId || !form.type) return
  try { categories.value = await api(`/categories?${query({ userId: form.userId, bookId: form.bookId, type: form.type })}`) }
  catch (e) { flash(e.message, true) }
}
async function loadUserLedgers() {
  userLedgers.value = []
  if (!form.userId || !modal.value) return
  try { userLedgers.value = await api(`/ledgers?${query({ userId: form.userId })}`) }
  catch (e) { flash(e.message, true) }
}
watch(() => form.userId, () => { if (!editingId.value) { form.bookId = ''; userLedgers.value = [] } })
watch(() => [form.bookId, form.type], () => { if (!editingId.value) form.categoryId = ''; if (modal.value) loadCategories() })
async function saveBill() {
  saving.value = true
  try {
    const payload = { ...form, occurredAt: form.occurredAt.replace('T', ' ') + (form.occurredAt.length === 16 ? ':00' : '') }
    await api(editingId.value ? `/bills/${editingId.value}` : '/bills',
      { method: editingId.value ? 'PUT' : 'POST', body: JSON.stringify(payload) })
    modal.value = false; flash(editingId.value ? '账单已更新' : '账单已添加'); await loadCurrent()
  } catch (e) { flash(e.message, true) }
  finally { saving.value = false }
}
async function deleteBill(item) {
  if (!window.confirm(`确定删除「${item.purpose}」这笔账单吗？`)) return
  try { await api(`/bills/${item.id}`, { method: 'DELETE' }); flash('账单已删除'); await loadCurrent() }
  catch (e) { flash(e.message, true) }
}
</script>

<template>
  <div v-if="!loggedIn" class="login-shell">
    <div class="login-ornament ornament-one"></div><div class="login-ornament ornament-two"></div>
    <form class="login-card" @submit.prevent="login">
      <div class="brand-mark">叶</div>
      <p class="eyebrow">QINGHE · BOOKKEEPING</p>
      <h1>欢迎回来</h1>
      <p class="muted">登录青禾记账管理后台，查看每一笔清晰的收支。</p>
      <label>管理员账号<input v-model.trim="loginForm.username" autocomplete="username" required placeholder="请输入账号" /></label>
      <label>登录密码<input v-model="loginForm.password" type="password" autocomplete="current-password" required placeholder="请输入密码" /></label>
      <button class="primary wide" :disabled="loading">{{ loading ? '登录中…' : '进入管理后台 →' }}</button>
      <p class="login-foot">安心记录 · 有序管理</p>
    </form>
    <div v-if="error" class="toast error">{{ error }}</div>
  </div>

  <div v-else class="shell">
    <aside class="sidebar">
      <div class="sidebar-brand"><div class="brand-mark small">叶</div><div><strong>青禾记账</strong><span>MANAGEMENT</span></div></div>
      <div class="nav-caption">工作台</div>
      <nav aria-label="主导航">
        <button v-for="item in visibleNav" :key="item.id" class="nav-item" :class="{ active: section === item.id }" @click="navigate(item.id)">
          <span class="nav-icon">{{ item.icon }}</span><span class="nav-text"><b>{{ item.label }}</b><small>{{ item.sub }}</small></span><span v-if="section === item.id" class="nav-chevron">›</span>
        </button>
      </nav>
      <div class="sidebar-note"><span class="note-dot"></span><div><strong>每一笔，都有迹可循</strong><small>让日常财务更清楚</small></div></div>
      <button class="sidebar-account" @click="logout"><span class="avatar">{{ admin?.username?.slice(0, 1)?.toUpperCase() }}</span><span><strong>{{ admin?.username }}</strong><small>管理员 · 退出登录</small></span><span class="account-arrow">↗</span></button>
    </aside>

    <main class="main">
      <header class="topbar"><div class="breadcrumb">工作台 <span>/</span> <strong>{{ current.label }}</strong></div><div class="topbar-right"><span class="live-dot"></span> 数据实时同步 <span class="topbar-divider"></span> {{ today }}</div></header>
      <div class="content">
        <div class="page-heading"><div><p class="eyebrow">{{ current.sub.toUpperCase() }}</p><h1>{{ current.label }}</h1><p class="muted">{{ section === 'dashboard' ? '从数据中了解每一天的收支动向。' : section === 'users' ? '查看并维护小程序用户资料与状态。' : '查找、整理并维护每一笔账单。' }}</p></div><button v-if="section === 'bills'" class="primary add-top" @click="openAdd">＋ 新增账单</button></div>

        <template v-if="section === 'dashboard'">
          <div v-if="dashboard" class="stats-grid">
            <div class="stat-card featured"><div class="stat-head"><span>今日支出</span><span class="stat-icon">↗</span></div><strong>{{ money(dashboard.todaySpend) }}</strong><small>今天共有 {{ dashboard.todayCount }} 笔支出</small><div class="card-leaf">✳</div></div>
            <div class="stat-card"><div class="stat-head"><span>本月支出</span><span class="soft-icon">◈</span></div><strong>{{ money(dashboard.monthSpend) }}</strong><small>截至今天的累计金额</small></div>
            <div class="stat-card"><div class="stat-head"><span>活跃用户</span><span class="soft-icon">♙</span></div><strong>{{ dashboard.activeUsers }} <em>人</em></strong><small>当前可使用小程序</small></div>
            <div class="stat-card"><div class="stat-head"><span>本月收入</span><span class="soft-icon">↙</span></div><strong>{{ money(dashboard.monthIncome) }}</strong><small>本月累计收入</small></div>
            <div class="stat-card"><div class="stat-head"><span>今日新增用户</span><span class="soft-icon">＋</span></div><strong>{{ dashboard.newUsersToday }} <em>人</em></strong><small>近 7 天登录 {{ dashboard.activeUsers7 }} 人</small></div>
            <div class="stat-card"><div class="stat-head"><span>本月记账人数</span><span class="soft-icon">▤</span></div><strong>{{ dashboard.bookkeepingUsersMonth }} <em>人</em></strong><small>本月记账 {{ dashboard.entriesMonth }} 笔</small></div>
          </div>
          <div class="dashboard-grid" v-if="dashboard">
            <section class="panel trend-panel"><div class="panel-heading"><div><h2>近 7 日支出趋势</h2><p>每天的支出金额变化</p></div><span class="panel-pill">最近 7 天</span></div><Chart :option="dailyOption" /></section>
            <section v-if="admin?.role === 'ADMIN'" class="panel purpose-panel"><div class="panel-heading"><div><h2>本月支出用途</h2><p>按用途汇总的支出占比</p></div></div><Chart v-if="dashboard.purpose.length" :option="purposeOption" /><div v-else class="empty-chart">本月暂无支出记录</div></section>
          </div>
          <section v-if="dashboard && admin?.role === 'ADMIN'" class="panel category-panel"><div class="panel-heading"><div><h2>本月分类概览</h2><p>各分类的支出汇总</p></div><button class="text-button" @click="navigate('bills')">查看全部账单 →</button></div><div v-if="dashboard.categories.length" class="category-list"><div v-for="row in dashboard.categories" :key="row.name" class="category-row"><span class="category-dot"></span><span>{{ row.name }}</span><div class="category-track"><i :style="{ width: `${Math.max(4, Number(row.amount) / Number(dashboard.monthSpend || 1) * 100)}%` }"></i></div><strong>{{ money(row.amount) }}</strong></div></div><div v-else class="empty-inline">暂无分类数据，新增账单后会显示在这里。</div></section>
          <section v-if="admin?.role === 'ADMIN'" class="panel category-panel"><div class="panel-heading"><div><h2>疑似重复账单</h2><p>同账本、同金额与用途，发生时间相差不超过 5 分钟</p></div></div><div v-if="anomalies.length" class="table-wrap"><table><thead><tr><th>用户</th><th>用途</th><th>金额</th><th>时间</th><th>操作</th></tr></thead><tbody><tr v-for="row in anomalies" :key="row.id"><td>{{ row.userName }}</td><td>{{ row.purpose }}</td><td>{{ money(row.amount) }}</td><td>{{ dateLabel(row.occurredAt) }}</td><td><button class="link-button" @click="openAnomaly(row)">查看账单</button></td></tr></tbody></table></div><div v-else class="empty-inline">当前没有疑似重复账单。</div></section>
        </template>

        <PlanningView v-if="section === 'planning'" />
        <SystemView v-if="section === 'system'" />
        <OperationsView v-if="section === 'operations'" />
        <LedgerView v-if="section === 'ledgers'" @open-bills="openLedgerBills" />

        <template v-if="section === 'users'">
          <section class="panel table-panel"><div class="table-toolbar"><div><h2>用户列表</h2><p>共 {{ users.total }} 位用户</p></div><form class="search-row" @submit.prevent="searchUsers"><input v-model="userFilter.search" placeholder="搜索昵称或用户 ID" aria-label="搜索用户" /><button class="secondary">搜索</button></form></div>
            <div class="table-wrap"><table><thead><tr><th>用户</th><th>用户 ID</th><th>账单数</th><th>累计支出</th><th>注册时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in users.items" :key="user.id"><td><div class="user-cell"><span class="list-avatar">{{ user.displayName.slice(0, 1) }}</span><strong>{{ user.displayName }}</strong></div></td><td class="mono">#{{ user.id }}</td><td>{{ user.billCount }}</td><td class="money-cell">{{ money(user.spend) }}</td><td>{{ dateLabel(user.createdAt) }}</td><td><span class="status" :class="user.status === 'ACTIVE' ? 'good' : 'disabled'">{{ user.status === 'ACTIVE' ? '正常' : user.status === 'CANCELLED' ? '已注销' : '已停用' }}</span></td><td><button v-if="admin?.role === 'ADMIN'" class="link-button" @click="openUserLogins(user)">登录记录</button><button v-if="admin?.role === 'ADMIN'" class="link-button" @click="renameUser(user)">编辑</button><button v-if="admin?.role === 'ADMIN' && user.status !== 'CANCELLED'" class="link-button" @click="toggleUser(user)">{{ user.status === 'ACTIVE' ? '停用' : '启用' }}</button><span v-if="admin?.role !== 'ADMIN'" class="subtle">仅查看</span></td></tr><tr v-if="!users.items.length"><td colspan="7" class="empty-table">暂无符合条件的用户</td></tr></tbody></table></div>
            <div class="pagination"><span>共 {{ users.total }} 条</span><div><button :disabled="users.page <= 1" @click="changePage('users', -1)">‹</button><span>{{ users.page }} / {{ pages(users) }}</span><button :disabled="users.page >= pages(users)" @click="changePage('users', 1)">›</button></div></div>
          </section>
        </template>

        <template v-if="section === 'bills'">
          <section class="panel table-panel"><div class="table-toolbar"><div><h2>账单明细</h2><p>共 {{ bills.total }} 笔记录</p></div><div><span class="toolbar-hint">支持按用途、备注、昵称或账单 ID 搜索</span><button class="secondary export-button" @click="exportBills">导出 CSV</button></div></div>
            <form class="filter-bar" @submit.prevent="searchBills"><div class="bill-search"><input v-model="billFilter.search" placeholder="搜索关键词" aria-label="账单关键词" /><button class="secondary">查询</button></div><select v-model="billFilter.bookId" aria-label="账本"><option value="">全部账本</option><option v-for="book in ledgerOptions" :key="book.id" :value="book.id">{{ book.name }} #{{ book.id }}</option></select><select v-model="billFilter.type" aria-label="收支类型"><option value="">全部类型</option><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select><input v-model="billFilter.from" type="date" aria-label="开始日期" /><span class="date-separator">至</span><input v-model="billFilter.to" type="date" aria-label="结束日期" /></form>
            <div class="table-wrap"><table><thead><tr><th>用途 / 备注</th><th>用户</th><th>分类</th><th>金额</th><th>发生时间</th><th>来源</th><th>操作</th></tr></thead><tbody><tr v-for="bill in bills.items" :key="bill.id"><td><div class="bill-purpose"><strong>{{ bill.purpose }}</strong><small>#{{ bill.id }} · {{ bill.note || '无备注' }}</small></div></td><td>{{ bill.userName }} <small class="subtle">#{{ bill.userId }}</small></td><td><span class="category-tag">{{ bill.category }}</span></td><td class="amount" :class="bill.type === 'INCOME' ? 'income' : ''">{{ bill.type === 'INCOME' ? '+' : '−' }}{{ money(bill.amount) }}</td><td>{{ dateLabel(bill.occurredAt) }}</td><td>{{ sourceLabel(bill.source) }}</td><td><button class="link-button" @click="openEdit(bill)">编辑</button><button class="link-button danger" @click="deleteBill(bill)">删除</button></td></tr><tr v-if="!bills.items.length"><td colspan="7" class="empty-table">暂无符合条件的账单</td></tr></tbody></table></div>
            <div class="pagination"><span>共 {{ bills.total }} 条</span><div><button :disabled="bills.page <= 1" @click="changePage('bills', -1)">‹</button><span>{{ bills.page }} / {{ pages(bills) }}</span><button :disabled="bills.page >= pages(bills)" @click="changePage('bills', 1)">›</button></div></div>
          </section>
        </template>
      </div>
    </main>

    <div v-if="modal" class="modal-backdrop" @click.self="modal = false">
      <form class="modal" @submit.prevent="saveBill">
        <div class="modal-head"><div><p class="eyebrow">BILL EDITOR</p><h2>{{ editingId ? '编辑账单' : '新增账单' }}</h2></div><button type="button" class="close" aria-label="关闭" @click="modal = false">×</button></div>
        <div class="form-grid">
          <label>用户 ID <span>*</span><input v-model="form.userId" type="number" min="1" :disabled="Boolean(editingId)" required placeholder="输入用户中心显示的 ID" @blur="loadUserLedgers" /></label>
          <label>账本 <span>*</span><select v-model="form.bookId" :disabled="Boolean(editingId)" required><option value="">请选择该用户的账本</option><option v-for="book in userLedgers" :key="book.id" :value="book.id">{{ book.name }} #{{ book.id }}</option></select></label>
          <label>收支类型 <span>*</span><select v-model="form.type"><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select></label>
          <label>金额 <span>*</span><input v-model="form.amount" type="number" min="0.01" step="0.01" required placeholder="0.00" /></label>
          <label>用途 <span>*</span><input v-model.trim="form.purpose" maxlength="40" required placeholder="例如 午餐" /></label>
          <label>分类<select v-model="form.categoryId"><option value="">默认：其他</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
          <label>发生时间 <span>*</span><input v-model="form.occurredAt" type="datetime-local" required /></label>
          <label class="full">备注<textarea v-model="form.note" rows="3" maxlength="500" placeholder="选填，最多 500 字"></textarea></label>
        </div>
        <div class="modal-actions"><button type="button" class="secondary" @click="modal = false">取消</button><button class="primary" :disabled="saving">{{ saving ? '保存中…' : '保存账单' }}</button></div>
      </form>
    </div>
    <div v-if="loginHistoryUser" class="modal-backdrop" @click.self="loginHistoryUser = null"><div class="modal"><div class="modal-head"><div><p class="eyebrow">LOGIN HISTORY</p><h2>{{ loginHistoryUser.displayName }} 的登录记录</h2></div><button class="close" @click="loginHistoryUser = null">×</button></div><div v-if="!userLogins.length" class="empty-inline">暂无登录记录</div><div v-for="entry in userLogins" :key="entry.id" class="history-row"><strong>{{ entry.createdAt }}</strong><span>{{ entry.device || '未知设备' }} · {{ entry.ip }}</span></div></div></div>
    <div v-if="notice || error" class="toast" :class="{ error: Boolean(error) }">{{ error || notice }}</div>
  </div>
</template>
