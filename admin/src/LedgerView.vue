<script setup>
import { onMounted, ref } from 'vue'
import { api, query } from './api'
const emit = defineEmits(['open-bills'])
const search = ref(''), ledgers = ref([]), error = ref('')
async function load() {
  try { ledgers.value = await api(`/ledgers?${query({ search: search.value })}`); error.value = '' }
  catch (e) { error.value = e.message }
}
onMounted(load)
</script>
<template>
  <section class="panel ledger-view"><div class="ledger-head"><div><h2>账本列表</h2><p>显示最近 200 个账本</p></div><form @submit.prevent="load"><input v-model="search" placeholder="搜索账本或用户昵称" /><button class="secondary">搜索</button></form></div><p v-if="error" class="ledger-error">{{ error }}</p><div class="table-wrap"><table><thead><tr><th>账本</th><th>创建者</th><th>共享成员</th><th>账单</th><th>操作</th></tr></thead><tbody><tr v-for="book in ledgers" :key="book.id"><td><strong>{{ book.name }}</strong> <small>#{{ book.id }}</small></td><td>{{ book.userName }} #{{ book.userId }}</td><td>{{ book.memberCount }}</td><td>{{ book.billCount }}</td><td><button class="link-button" @click="emit('open-bills', book.id)">查看账单 →</button></td></tr><tr v-if="!ledgers.length"><td colspan="5" class="empty-table">暂无账本</td></tr></tbody></table></div></section>
</template>
<style scoped>.ledger-view{overflow:hidden}.ledger-head{padding:22px;display:flex;justify-content:space-between;gap:15px;align-items:center}.ledger-head p{color:#9cac9e;font-size:11px;margin-top:6px}.ledger-head form{display:flex;gap:8px}.ledger-head input{min-width:220px}.ledger-error{color:#ba756a;padding:0 22px 15px;font-size:12px}@media(max-width:700px){.ledger-head{align-items:start;flex-direction:column}.ledger-head form{width:100%}.ledger-head input{min-width:0;flex:1}}</style>
