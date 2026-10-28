<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api } from './api'
const tab = ref('feedback'), feedback = ref([]), categories = ref([]), contents = ref([]), notice = ref(''), error = ref('')
const form = reactive({ type: 'EXPENSE', name: '', icon: '•', sortOrder: 0 })
const contentForm = reactive({ id: null, kind: 'ANNOUNCEMENT', title: '', body: '', linkPath: '', sortOrder: 0, active: false })
async function load() {
  try { [feedback.value, categories.value, contents.value] = await Promise.all([api('/feedback'), api('/system-categories'), api('/content')]) }
  catch (e) { error.value = e.message }
}
onMounted(load)
async function resolve(row) {
  const response = window.prompt(`回复「${row.subject}」`, row.response || '')
  if (response === null) return
  try { await api(`/feedback/${row.id}`, { method: 'PUT', body: JSON.stringify({ response }) }); notice.value = '反馈已回复'; error.value = ''; load() }
  catch (e) { error.value = e.message }
}
async function addCategory() {
  try { await api('/system-categories', { method: 'POST', body: JSON.stringify(form) }); notice.value = '默认分类已创建'; error.value = ''; form.name = ''; form.icon = '•'; form.sortOrder = 0; load() }
  catch (e) { error.value = e.message }
}
async function editCategory(row, toggle = false) {
  const name = toggle ? row.name : window.prompt('分类名称', row.name)
  if (name === null) return
  const icon = toggle ? row.icon : window.prompt('图标', row.icon)
  if (icon === null) return
  const sortOrder = toggle ? row.sortOrder : Number(window.prompt('排序数字', row.sortOrder))
  if (!Number.isInteger(sortOrder)) return
  try { await api(`/system-categories/${row.id}`, { method: 'PUT', body: JSON.stringify({ name, icon, sortOrder, active: toggle ? !row.active : row.active }) }); notice.value = '默认分类已更新'; error.value = ''; load() }
  catch (e) { error.value = e.message }
}
function editContent(row) { Object.assign(contentForm, row); tab.value = 'content' }
async function saveContent() {
  try {
    await api(contentForm.id ? `/content/${contentForm.id}` : '/content',
      { method: contentForm.id ? 'PUT' : 'POST', body: JSON.stringify(contentForm) })
    notice.value = '内容已保存'; error.value = ''
    Object.assign(contentForm, { id: null, kind: 'ANNOUNCEMENT', title: '', body: '', linkPath: '', sortOrder: 0, active: false }); load()
  } catch (e) { error.value = e.message }
}
async function toggleContent(row) {
  try { await api(`/content/${row.id}`, { method: 'PUT', body: JSON.stringify({ ...row, active: !row.active }) }); notice.value = '发布状态已更新'; error.value = ''; load() }
  catch (e) { error.value = e.message }
}
</script>
<template>
  <div class="ops"><div v-if="notice || error" class="ops-message" :class="{ bad: error }">{{ error || notice }}</div><div class="ops-tabs"><button :class="{ active: tab === 'feedback' }" @click="tab = 'feedback'">用户反馈</button><button :class="{ active: tab === 'categories' }" @click="tab = 'categories'">系统默认分类</button><button :class="{ active: tab === 'content' }" @click="tab = 'content'">公告与帮助</button></div>
    <section v-if="tab === 'feedback'" class="panel ops-panel"><h2>反馈处理 <small>{{ feedback.filter(item => item.status === 'OPEN').length }} 条待处理</small></h2><div v-if="!feedback.length" class="ops-empty">还没有用户反馈。</div><div v-for="item in feedback" :key="item.id" class="ops-row"><div><strong>{{ item.subject }}</strong><p>{{ item.content }}</p><small>{{ item.userName }} #{{ item.userId }} · {{ item.createdAt }} · {{ item.status === 'OPEN' ? '待处理' : '已回复' }}</small><p v-if="item.response" class="response">回复：{{ item.response }}</p></div><button class="link-button" @click="resolve(item)">{{ item.status === 'OPEN' ? '回复' : '修改回复' }}</button></div></section>
    <div v-if="tab === 'categories'" class="ops-grid"><section class="panel ops-panel"><h2>新账本默认分类</h2><p class="hint">修改只影响之后创建的账本；现有账本保留自己的分类。</p><div v-for="item in categories" :key="item.id" class="ops-row"><div><strong>{{ item.icon }} {{ item.name }}</strong><small>{{ item.type === 'EXPENSE' ? '支出' : '收入' }} · 排序 {{ item.sortOrder }} · {{ item.active ? '启用' : '停用' }}</small></div><div><button class="link-button" @click="editCategory(item)">编辑</button><button class="link-button" @click="editCategory(item, true)">{{ item.active ? '停用' : '启用' }}</button></div></div></section><form class="panel ops-panel ops-form" @submit.prevent="addCategory"><h2>新增默认分类</h2><label>类型<select v-model="form.type"><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select></label><label>名称<input v-model="form.name" maxlength="50" required /></label><label>图标<input v-model="form.icon" maxlength="32" required /></label><label>排序<input v-model="form.sortOrder" type="number" required /></label><button class="primary">创建分类</button></form></div>
    <div v-if="tab === 'content'" class="ops-grid"><section class="panel ops-panel"><h2>已配置内容</h2><p class="hint">发布后会显示在小程序帮助中心。</p><div v-if="!contents.length" class="ops-empty">尚未创建内容。</div><div v-for="item in contents" :key="item.id" class="ops-row"><div><strong>{{ item.title }}</strong><small>{{ item.kind }} · {{ item.active ? '已发布' : '草稿' }} · {{ item.updatedAt }}</small><p>{{ item.body.slice(0, 100) }}{{ item.body.length > 100 ? '…' : '' }}</p></div><div><button class="link-button" @click="editContent(item)">编辑</button><button class="link-button" @click="toggleContent(item)">{{ item.active ? '下线' : '发布' }}</button></div></div></section><form class="panel ops-panel ops-form" @submit.prevent="saveContent"><h2>{{ contentForm.id ? '编辑内容' : '新增内容' }}</h2><label>类型<select v-model="contentForm.kind"><option value="ANNOUNCEMENT">公告</option><option value="BANNER">首页横幅</option><option value="FAQ">帮助问答</option><option value="VERSION">版本提示</option><option value="PRIVACY">隐私政策</option><option value="TERMS">用户协议</option></select></label><label>标题<input v-model="contentForm.title" maxlength="100" required /></label><label>正文<textarea v-model="contentForm.body" rows="6" maxlength="10000" required /></label><label>小程序内部页面路径（可选）<input v-model="contentForm.linkPath" placeholder="pages/ledger/index" /></label><label>排序<input v-model="contentForm.sortOrder" type="number" /></label><label class="check-row"><input v-model="contentForm.active" type="checkbox" />立即发布</label><button class="primary">保存内容</button></form></div>
  </div>
</template>
<style scoped>
.ops-tabs{display:flex;gap:6px;margin-bottom:19px}.ops-tabs button{border:0;background:#eaf3e9;color:#749079;border-radius:9px;padding:10px 16px;font-size:12px}.ops-tabs button.active{background:#5ca973;color:#fff}.ops-panel{padding:23px}.ops-panel h2 small{font-weight:400;color:#8ea28f;margin-left:10px}.ops-row{border-top:1px solid #edf2ec;padding:17px 0;display:flex;justify-content:space-between;gap:17px;align-items:start}.ops-row:first-of-type{margin-top:18px}.ops-row strong{font-size:13px}.ops-row p{font-size:12px;line-height:1.6;color:#657d6b;margin-top:7px}.ops-row small{font-size:11px;color:#a1b0a3;display:block;margin-top:7px}.ops-row .response{color:#5b986c;background:#f0f7ef;padding:8px 11px;border-radius:7px}.ops-row>div:last-child{white-space:nowrap}.ops-empty{text-align:center;color:#9aaa9d;padding:55px}.ops-grid{display:grid;grid-template-columns:1.5fr 1fr;gap:17px;align-items:start}.hint{font-size:11px;color:#9bad9d;margin-top:7px}.ops-form{display:grid;gap:15px}.ops-form label{color:#79907d;font-size:11px}.ops-form input,.ops-form select,.ops-form textarea{display:block;width:100%;margin-top:7px}.ops-form .check-row{display:flex;align-items:center;gap:8px}.ops-form .check-row input{width:auto;margin:0}@media(max-width:900px){.ops-grid{grid-template-columns:1fr}}
.ops-message{padding:11px 15px;background:#ecf7ec;color:#508e5d;border-radius:8px;margin-bottom:15px;font-size:12px}.ops-message.bad{background:#faecea;color:#be766c}
</style>
