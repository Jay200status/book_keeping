<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api } from './api'
const tab = ref('admins'), admins = ref([]), logins = ref([]), audits = ref([]), message = ref(''), error = ref('')
const form = reactive({ username: '', password: '', role: 'OPERATOR' })
async function load() {
  try { [admins.value, logins.value, audits.value] = await Promise.all([api('/admin-users'), api('/login-logs'), api('/audit-logs')]) }
  catch (e) { error.value = e.message }
}
onMounted(load)
async function add() {
  try { await api('/admin-users', { method: 'POST', body: JSON.stringify(form) }); form.username = ''; form.password = ''; message.value = '管理员已创建'; error.value = ''; load() }
  catch (e) { error.value = e.message }
}
async function update(item, field) {
  const next = { role: item.role, active: item.active }
  if (field === 'role') next.role = item.role === 'ADMIN' ? 'OPERATOR' : 'ADMIN'
  else next.active = !item.active
  try { await api(`/admin-users/${item.id}`, { method: 'PATCH', body: JSON.stringify(next) }); message.value = '权限已更新'; error.value = ''; load() }
  catch (e) { error.value = e.message }
}
</script>
<template>
  <div class="system-view"><div v-if="message || error" class="system-message" :class="{ bad: error }">{{ error || message }}</div>
    <div class="system-tabs"><button :class="{ active: tab === 'admins' }" @click="tab = 'admins'">管理员账号</button><button :class="{ active: tab === 'logins' }" @click="tab = 'logins'">登录记录</button><button :class="{ active: tab === 'audits' }" @click="tab = 'audits'">操作审计</button></div>
    <template v-if="tab === 'admins'"><div class="system-grid"><section class="panel system-panel"><h2>管理员列表</h2><div v-for="item in admins" :key="item.id" class="system-row"><div><strong>{{ item.username }}</strong><small>#{{ item.id }} · {{ item.role === 'ADMIN' ? '管理员' : '操作员' }} · {{ item.active ? '启用' : '停用' }}</small></div><div><button class="link-button" @click="update(item, 'role')">切换角色</button><button class="link-button" @click="update(item, 'active')">{{ item.active ? '停用' : '启用' }}</button></div></div></section><form class="panel system-panel system-form" @submit.prevent="add"><h2>新增管理员</h2><label>账号<input v-model="form.username" minlength="3" maxlength="64" required placeholder="字母、数字、.、_ 或 -" /></label><label>密码<input v-model="form.password" type="password" minlength="12" required placeholder="至少 12 位" /></label><label>角色<select v-model="form.role"><option value="OPERATOR">操作员</option><option value="ADMIN">管理员</option></select></label><button class="primary">创建账号</button></form></div></template>
    <section v-if="tab === 'logins'" class="panel system-panel"><h2>最近 100 次登录</h2><div class="table-wrap"><table><thead><tr><th>时间</th><th>账号</th><th>结果</th><th>IP 地址</th><th>设备</th></tr></thead><tbody><tr v-for="item in logins" :key="item.id"><td>{{ item.createdAt }}</td><td>{{ item.username }}</td><td><span class="status" :class="item.success ? 'good' : 'disabled'">{{ item.success ? '成功' : '失败' }}</span></td><td>{{ item.ip }}</td><td>{{ item.userAgent }}</td></tr></tbody></table></div></section>
    <section v-if="tab === 'audits'" class="panel system-panel"><h2>最近 100 条操作记录</h2><div class="table-wrap"><table><thead><tr><th>时间</th><th>管理员</th><th>操作</th><th>对象</th><th>ID</th></tr></thead><tbody><tr v-for="item in audits" :key="item.id"><td>{{ item.createdAt }}</td><td>{{ item.admin }}</td><td>{{ item.action }}</td><td>{{ item.targetType }}</td><td>#{{ item.targetId }}</td></tr></tbody></table></div></section>
  </div>
</template>
<style scoped>
.system-tabs{display:flex;gap:6px;margin-bottom:19px}.system-tabs button{border:0;background:#eaf3e9;color:#749079;border-radius:9px;padding:10px 16px;font-size:12px}.system-tabs button.active{background:#5ca973;color:#fff}.system-grid{display:grid;grid-template-columns:1.5fr 1fr;gap:17px;align-items:start}.system-panel{padding:23px}.system-row{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #edf2ec;padding:16px 0}.system-row:first-of-type{margin-top:18px}.system-row strong,.system-row small{display:block}.system-row strong{font-size:13px}.system-row small{color:#9ba99d;margin-top:5px}.system-form{display:grid;gap:15px}.system-form label{font-size:11px;color:#78907c}.system-form input,.system-form select{width:100%;display:block;margin-top:7px}.system-message{background:#eaf5e9;color:#518e5f;border-radius:8px;padding:11px 15px;margin-bottom:15px;font-size:12px}.system-message.bad{background:#faecea;color:#bf756d}@media(max-width:900px){.system-grid{grid-template-columns:1fr}}
</style>
