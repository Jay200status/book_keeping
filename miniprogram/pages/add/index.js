const { request } = require('../../utils/api');
const { selectedBook, chooseBook, path, today, currentTime } = require('../../utils/finance');
Page({
  data: { books: [], bookIndex: 0, bookId: 0, type: 'EXPENSE', amount: '', purpose: '', note: '', date: today(),
    time: currentTime(), categories: [], categoryIndex: 0, accounts: [], accountIndex: 0, toAccountIndex: 0,
    templates: [], saving: false, loading: true },
  onShow() { if (this.getTabBar()) this.getTabBar().setData({ selected: 1 }); this.load(); },
  async load() {
    try {
      const { books, book } = await selectedBook();
      if (!book) return this.setData({ books, loading: false });
      this.setData({ books, bookId: book.id, bookIndex: books.findIndex(item => item.id === book.id), loading: false });
      await this.loadOptions();
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); this.setData({ loading: false }); }
  },
  async loadOptions() {
    const { bookId, type } = this.data;
    if (!bookId) return;
    try {
      const [categories, accounts, templates] = await Promise.all([
        request(path(bookId, `/categories?type=${type === 'TRANSFER' ? 'EXPENSE' : type}`)),
        request(path(bookId, '/accounts')), request(path(bookId, '/templates'))
      ]);
      if (this.data.bookId !== bookId || this.data.type !== type) return;
      const activeAccounts = accounts.filter(a => a.active);
      this.setData({ categories: categories.filter(c => c.active), accounts: activeAccounts,
        templates: templates.filter(t => t.type === type), categoryIndex: 0, accountIndex: 0,
        toAccountIndex: activeAccounts.length > 1 ? 1 : 0 });
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  pickBook(event) { const index = Number(event.detail.value), book = this.data.books[index]; if (!book) return;
    chooseBook(book.id); this.setData({ bookId: book.id, bookIndex: index }); this.loadOptions(); },
  setType(event) { this.setData({ type: event.currentTarget.dataset.type }); this.loadOptions(); },
  setAmount(event) { this.setData({ amount: event.detail.value }); },
  setPurpose(event) { this.setData({ purpose: event.detail.value }); },
  setNote(event) { this.setData({ note: event.detail.value }); },
  pickDate(event) { this.setData({ date: event.detail.value }); },
  pickTime(event) { this.setData({ time: event.detail.value }); },
  pickCategory(event) { this.setData({ categoryIndex: Number(event.detail.value) }); },
  pickAccount(event) { this.setData({ accountIndex: Number(event.detail.value) }); },
  pickToAccount(event) { this.setData({ toAccountIndex: Number(event.detail.value) }); },
  useTemplate(event) {
    const template = this.data.templates.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (!template) return;
    this.setData({ amount: template.amount, purpose: template.purpose, note: template.note,
      categoryIndex: Math.max(0, this.data.categories.findIndex(c => c.id === template.categoryId)),
      accountIndex: Math.max(0, this.data.accounts.findIndex(a => a.id === template.accountId)) });
  },
  async saveTemplate() {
    const { bookId, type, amount, purpose, note, categories, categoryIndex, accounts, accountIndex } = this.data;
    if (!purpose.trim()) return wx.showToast({ title: '先填写用途', icon: 'none' });
    try {
      await request(path(bookId, '/templates'), 'POST', { type, name: purpose.trim(), amount: amount || null,
        purpose: purpose.trim(), categoryId: categories[categoryIndex]?.id, accountId: accounts[accountIndex]?.id, note });
      wx.showToast({ title: '已保存为模板', icon: 'success' }); this.loadOptions();
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  async save() {
    if (this.data.saving) return;
    const { bookId, type, amount, purpose, note, date, time, categories, categoryIndex, accounts, accountIndex, toAccountIndex } = this.data;
    if (!bookId) return wx.showToast({ title: '请先创建账本', icon: 'none' });
    if (!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) return wx.showToast({ title: '请输入正确金额', icon: 'none' });
    if (type !== 'TRANSFER' && !purpose.trim()) return wx.showToast({ title: '请输入用途', icon: 'none' });
    this.setData({ saving: true });
    try {
      const occurredAt = `${date} ${time}:00`;
      if (type === 'TRANSFER') {
        if (!accounts[accountIndex] || !accounts[toAccountIndex] || accountIndex === toAccountIndex) throw new Error('请选择两个不同的账户');
        await request(path(bookId, '/transfers'), 'POST', { amount, occurredAt, note,
          fromAccountId: accounts[accountIndex].id, toAccountId: accounts[toAccountIndex].id });
      } else {
        await request(path(bookId, '/records'), 'POST', { type, amount, purpose: purpose.trim(), note, occurredAt,
          categoryId: categories[categoryIndex]?.id, accountId: accounts[accountIndex]?.id });
      }
      this.setData({ amount: '', purpose: '', note: '' }); wx.showToast({ title: '已记下', icon: 'success' });
      setTimeout(() => wx.switchTab({ url: '/pages/ledger/index' }), 350);
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
    finally { this.setData({ saving: false }); }
  }
});
