const { request } = require('../../utils/api');
const { path } = require('../../utils/finance');
Page({
  data: { record: null, error: '', editing: false, categories: [], accounts: [], categoryIndex: 0, accountIndex: 0,
    amount: '', purpose: '', note: '', date: '', time: '', saving: false, comments: [], commentText: '', commentSaving: false },
  onLoad() {
    const record = getApp().globalData.selectedTransaction;
    if (!record) return this.setData({ error: '请从账单列表打开详情' });
    this.setData({ record, amount: record.amount, purpose: record.purpose, note: record.note || '',
      date: record.occurredAt.slice(0, 10), time: record.occurredAt.slice(11, 16) });
    if (record.kind !== 'transfer') this.loadComments();
  },
  async loadComments() {
    const { record } = this.data;
    try { this.setData({ comments: await request(path(record.bookId, `/records/${record.id}/comments`)) }); }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  setComment(event) { this.setData({ commentText: event.detail.value }); },
  async addComment() {
    const { record, commentText } = this.data;
    if (!commentText.trim()) return wx.showToast({ title: '请输入留言', icon: 'none' });
    this.setData({ commentSaving: true });
    try { await request(path(record.bookId, `/records/${record.id}/comments`), 'POST', { body: commentText.trim() });
      this.setData({ commentText: '' }); await this.loadComments(); }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
    finally { this.setData({ commentSaving: false }); }
  },
  async edit() {
    const { record } = this.data;
    try {
      const [categories, accounts] = await Promise.all([
        request(path(record.bookId, `/categories?type=${record.type}`)), request(path(record.bookId, '/accounts'))
      ]);
      const activeCategories = categories.filter(c => c.active), activeAccounts = [{ id: '', name: '不指定' }, ...accounts.filter(a => a.active)];
      this.setData({ editing: true, categories: activeCategories, accounts: activeAccounts,
        categoryIndex: Math.max(0, activeCategories.findIndex(c => c.id === record.categoryId)),
        accountIndex: Math.max(0, activeAccounts.findIndex(a => a.id === record.accountId)) });
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  setAmount(event) { this.setData({ amount: event.detail.value }); },
  setPurpose(event) { this.setData({ purpose: event.detail.value }); },
  setNote(event) { this.setData({ note: event.detail.value }); },
  pickDate(event) { this.setData({ date: event.detail.value }); },
  pickTime(event) { this.setData({ time: event.detail.value }); },
  pickCategory(event) { this.setData({ categoryIndex: Number(event.detail.value) }); },
  pickAccount(event) { this.setData({ accountIndex: Number(event.detail.value) }); },
  cancel() { this.setData({ editing: false }); },
  async save() {
    const { record, amount, purpose, note, date, time, categories, categoryIndex, accounts, accountIndex } = this.data;
    this.setData({ saving: true });
    try {
      await request(path(record.bookId, `/records/${record.id}`), 'PUT', { type: record.type, amount, purpose,
        note, occurredAt: `${date} ${time}:00`, categoryId: categories[categoryIndex]?.id, accountId: accounts[accountIndex]?.id });
      wx.showToast({ title: '已更新', icon: 'success' }); wx.navigateBack();
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
    finally { this.setData({ saving: false }); }
  },
  remove() {
    const { record } = this.data;
    wx.showModal({ title: '删除账单', content: '删除后将不再计入统计，确定继续？', success: async result => {
      if (!result.confirm) return;
      try {
        await request(path(record.bookId, record.kind === 'transfer' ? `/transfers/${record.id}` : `/records/${record.id}`), 'DELETE');
        wx.showToast({ title: '已删除', icon: 'success' }); wx.navigateBack();
      } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
    } });
  }
});
