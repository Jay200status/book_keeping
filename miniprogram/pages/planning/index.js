const { request } = require('../../utils/api');
const { selectedBook, chooseBook, path, today, currentTime } = require('../../utils/finance');
Page({
  data: {
    tab: 'budgets', books: [], bookIndex: 0, bookId: 0, role: '', month: today().slice(0, 7),
    budgets: [], reminders: [], recurring: [], pending: [], categories: [], expenseOptions: [], recurringOptions: [],
    budgetCategoryName: '月度总预算', recurringCategoryName: '默认：其他',
    reminderKinds: ['记账提醒', '账单到期'], reminderRecurrences: ['仅一次', '每天', '每月'], transactionTypes: ['支出', '收入'],
    budgetForm: { categoryId: '', amount: '' },
    reminderForm: { kind: 'BOOKKEEPING', title: '', date: today(), time: currentTime(), recurrence: 'DAILY', leadDays: 0 },
    recurringForm: { type: 'EXPENSE', purpose: '', amount: '', categoryId: '', dayOfMonth: 1, startMonth: today().slice(0, 7), endMonth: '', note: '' }
  },
  async onShow() { try { const { books, book } = await selectedBook();
    if (!book) return; this.setData({ books, bookId: book.id, role: book.role, bookIndex: books.findIndex(item => item.id === book.id) }); this.load(); }
    catch (error) { this.toast(error); } },
  toast(error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); },
  tab(event) { this.setData({ tab: event.currentTarget.dataset.tab }); },
  pickBook(event) { const index = Number(event.detail.value), book = this.data.books[index]; if (!book) return;
    chooseBook(book.id); this.setData({ bookId: book.id, bookIndex: index, role: book.role }); this.load(); },
  pickMonth(event) { this.setData({ month: event.detail.value }); this.load(); },
  async load() {
    const { bookId, month, role } = this.data; if (!bookId) return;
    try {
      const [budgets, reminders, categories] = await Promise.all([
        request(path(bookId, `/budgets?month=${month}`)), request('/api/finance/reminders'), request(path(bookId, '/categories'))
      ]);
      const [recurring, pending] = role === 'OWNER' ? await Promise.all([
        request(path(bookId, '/recurring-bills')), request(path(bookId, '/pending-bills'))
      ]) : [[], []];
      if (this.data.bookId !== bookId) return;
      const active = categories.filter(c => c.active);
      this.setData({ budgets, reminders, categories: active,
        expenseOptions: [{ id: '', name: '月度总预算' }, ...active.filter(c => c.type === 'EXPENSE')],
        recurringOptions: [{ id: '', name: '默认：其他' }, ...active.filter(c => c.type === this.data.recurringForm.type)],
        recurring, pending });
    } catch (error) { this.toast(error); }
  },
  field(event) { const { group, field } = event.currentTarget.dataset; this.setData({ [`${group}.${field}`]: event.detail.value }); },
  budgetCategory(event) { const index = Number(event.detail.value), option = this.data.expenseOptions[index]; this.setData({ 'budgetForm.categoryId': option?.id || '', budgetCategoryName: option?.name || '月度总预算' }); },
  recurringCategory(event) { const index = Number(event.detail.value), option = this.data.recurringOptions[index]; this.setData({ 'recurringForm.categoryId': option?.id || '', recurringCategoryName: option?.name || '默认：其他' }); },
  recurringType(event) { const type = Number(event.detail.value) ? 'INCOME' : 'EXPENSE'; this.setData({ 'recurringForm.type': type,
    'recurringForm.categoryId': '', recurringCategoryName: '默认：其他', recurringOptions: [{ id: '', name: '默认：其他' }, ...this.data.categories.filter(c => c.type === type)] }); },
  reminderKind(event) { this.setData({ 'reminderForm.kind': Number(event.detail.value) ? 'DUE' : 'BOOKKEEPING' }); },
  reminderRecurrence(event) { this.setData({ 'reminderForm.recurrence': ['ONCE', 'DAILY', 'MONTHLY'][Number(event.detail.value)] }); },
  async saveBudget() {
    try { await request(path(this.data.bookId, '/budgets'), 'POST', { ...this.data.budgetForm, month: this.data.month });
      wx.showToast({ title: '预算已保存', icon: 'success' }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async removeBudget(event) {
    try { await request(path(this.data.bookId, `/budgets/${event.currentTarget.dataset.id}`), 'DELETE'); this.load(); }
    catch (error) { this.toast(error); }
  },
  async saveReminder() {
    const form = this.data.reminderForm;
    try { await request('/api/finance/reminders', 'POST', { kind: form.kind, title: form.title,
      dueAt: `${form.date} ${form.time}:00`, recurrence: form.recurrence, leadDays: form.leadDays });
      this.setData({ 'reminderForm.title': '' }); wx.showToast({ title: '提醒已创建', icon: 'success' }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async toggleReminder(event) {
    const row = this.data.reminders.find(item => item.id === Number(event.currentTarget.dataset.id)); if (!row) return;
    try { await request(`/api/finance/reminders/${row.id}`, 'PATCH', { active: !row.active }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async saveRecurring() {
    try { await request(path(this.data.bookId, '/recurring-bills'), 'POST', this.data.recurringForm);
      wx.showToast({ title: '模板已创建', icon: 'success' }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async toggleRecurring(event) {
    const row = this.data.recurring.find(item => item.id === Number(event.currentTarget.dataset.id)); if (!row) return;
    try { await request(path(this.data.bookId, `/recurring-bills/${row.id}`), 'PATCH', { active: !row.active }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async decide(event) {
    const { id, action } = event.currentTarget.dataset;
    try { await request(path(this.data.bookId, `/pending-bills/${id}/${action}`), 'POST', {});
      wx.showToast({ title: action === 'approve' ? '已入账' : '已忽略', icon: 'success' }); this.load(); }
    catch (error) { this.toast(error); }
  }
});
