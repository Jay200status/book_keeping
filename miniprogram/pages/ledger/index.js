const { request } = require('../../utils/api');
const { selectedBook, chooseBook, path, today } = require('../../utils/finance');
const pad = n => String(n).padStart(2, '0');
function range(period, value) {
  const [y, m, d] = value.split('-').map(Number);
  const start = period === 'year' ? new Date(y, 0, 1) : period === 'month' ? new Date(y, m - 1, 1) : new Date(y, m - 1, d);
  const end = new Date(start);
  if (period === 'year') end.setFullYear(y + 1);
  else if (period === 'month') end.setMonth(start.getMonth() + 1);
  else end.setDate(start.getDate() + 1);
  const fmt = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  const last = new Date(end); last.setDate(last.getDate() - 1);
  return { from: fmt(start), to: fmt(last) };
}
Page({
  data: { period: 'month', date: today(), title: '', expense: '0.00', income: '0.00', records: [],
    books: [], bookId: 0, bookIndex: 0, categories: [], accounts: [], members: [], budgets: [], banners: [],
    typeLabels: ['全部类型', '支出', '收入', '转账'],
    search: '', categoryIndex: 0, accountIndex: 0, memberIndex: 0, typeIndex: 0,
    loading: true, error: '' },
  onShow() { if (this.getTabBar()) this.getTabBar().setData({ selected: 0 }); this.initialize(); },
  async initialize() {
    try {
      const { books, book } = await selectedBook();
      if (!book) return this.setData({ books, loading: false });
      this.setData({ books, bookId: book.id, bookIndex: books.findIndex(item => item.id === book.id) });
      const [categories, accounts, members, banners] = await Promise.all([
        request(path(book.id, '/categories')), request(path(book.id, '/accounts')), request(path(book.id, '/members')),
        request('/api/content?kind=BANNER')
      ]);
      this.setData({ categories: [{ id: 0, name: '全部分类' }, ...categories], accounts: [{ id: 0, name: '全部账户' }, ...accounts],
        members: [{ id: 0, displayName: '全部成员' }, ...members], banners });
      await this.load();
    } catch (error) { this.setData({ loading: false, error: error.message }); }
  },
  pickBook(event) {
    const index = Number(event.detail.value), book = this.data.books[index]; if (!book) return;
    chooseBook(book.id); this.setData({ bookId: book.id, bookIndex: index, categoryIndex: 0, accountIndex: 0, memberIndex: 0 });
    this.initialize();
  },
  setPeriod(event) { this.setData({ period: event.currentTarget.dataset.period }); this.load(); },
  pickDate(event) { this.setData({ date: event.detail.value }); this.load(); },
  shift(event) {
    const offset = Number(event.currentTarget.dataset.offset);
    const [y, m, day] = this.data.date.split('-').map(Number);
    const date = new Date(y, m - 1, day);
    if (this.data.period === 'year') date.setFullYear(y + offset);
    else if (this.data.period === 'month') { date.setDate(1); date.setMonth(m - 1 + offset); }
    else date.setDate(day + offset);
    this.setData({ date: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` }); this.load();
  },
  setSearch(event) { this.setData({ search: event.detail.value }); },
  pickCategory(event) { this.setData({ categoryIndex: Number(event.detail.value) }); this.load(); },
  pickAccount(event) { this.setData({ accountIndex: Number(event.detail.value) }); this.load(); },
  pickMember(event) { this.setData({ memberIndex: Number(event.detail.value) }); this.load(); },
  pickType(event) { this.setData({ typeIndex: Number(event.detail.value) }); this.load(); },
  async load() {
    const { bookId, period, date, search, categories, categoryIndex, accounts, accountIndex, members, memberIndex, typeIndex } = this.data;
    if (!bookId) return;
    const title = period === 'year' ? `${date.slice(0, 4)} 年` : period === 'month' ? `${date.slice(0, 4)} 年 ${Number(date.slice(5, 7))} 月` : `${date.slice(0, 4)} 年 ${Number(date.slice(5, 7))} 月 ${Number(date.slice(8))} 日`;
    this.setData({ title, loading: true, error: '' });
    try {
      const dates = range(period, date), params = [];
      params.push(`from=${dates.from}`, `to=${dates.to}`);
      if (search.trim()) params.push(`search=${encodeURIComponent(search.trim())}`);
      if (categories[categoryIndex]?.id) params.push(`categoryId=${categories[categoryIndex].id}`);
      if (accounts[accountIndex]?.id) params.push(`accountId=${accounts[accountIndex].id}`);
      if (members[memberIndex]?.id) params.push(`memberId=${members[memberIndex].id}`);
      const [summary, result, budgets] = await Promise.all([
        request(path(bookId, `/summary?period=${period}&date=${date}`)),
        request(path(bookId, `/transactions?${params.join('&')}`)),
        request(path(bookId, `/budgets?month=${date.slice(0, 7)}`))
      ]);
      if (this.data.bookId !== bookId || this.data.period !== period || this.data.date !== date) return;
      const value = type => summary.totals.find(item => item.type === type)?.amount || '0.00';
      const records = result.records.map(item => ({ ...item, kind: 'record', icon: item.purpose.slice(0, 1),
        day: item.occurredAt.slice(5, 10), time: item.occurredAt.slice(11, 16) }));
      const transfers = result.transfers.map(item => ({ ...item, kind: 'transfer', type: 'TRANSFER', purpose: `${item.fromName} → ${item.toName}`,
        icon: '↔', day: item.occurredAt.slice(5, 10), time: item.occurredAt.slice(11, 16) }));
      const all = [...records, ...transfers].filter(item => typeIndex === 0 || item.type === ['ALL', 'EXPENSE', 'INCOME', 'TRANSFER'][typeIndex]);
      all.sort((a, b) => b.occurredAt.localeCompare(a.occurredAt) || b.id - a.id);
      this.setData({ expense: value('EXPENSE'), income: value('INCOME'), records: all, budgets, loading: false });
    } catch (error) { this.setData({ loading: false, error: error.message }); }
  },
  openDetail(event) {
    const item = this.data.records.find(row => row.id === Number(event.currentTarget.dataset.id) && row.kind === event.currentTarget.dataset.kind);
    if (!item) return;
    const canWrite = this.data.books[this.data.bookIndex]?.role !== 'VIEWER';
    getApp().globalData.selectedTransaction = { ...item, bookId: this.data.bookId, canEdit: item.canEdit && canWrite, canComment: canWrite };
    wx.navigateTo({ url: '/pages/detail/index' });
  },
  manage() { wx.navigateTo({ url: '/pages/manage/index' }); },
  openBanner(event) {
    const banner = this.data.banners.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (!banner?.linkPath) return;
    const url = `/${banner.linkPath}`;
    if (['/pages/ledger/index', '/pages/add/index', '/pages/me/index'].includes(url)) wx.switchTab({ url });
    else wx.navigateTo({ url });
  }
});
