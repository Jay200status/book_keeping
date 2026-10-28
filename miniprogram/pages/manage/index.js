const { request } = require('../../utils/api');
const { selectedBook, chooseBook, path } = require('../../utils/finance');
Page({
  data: { tab: 'books', books: [], bookId: 0, bookIndex: 0, role: '', newBookName: '', inviteCode: '', joinCode: '',
    members: [], categories: [], accounts: [], templates: [], categoryType: 'EXPENSE',
    assetTotal: '0.00', liabilityTotal: '0.00', netAssets: '0.00',
    categoryForm: { id: 0, name: '', icon: '•', sortOrder: 0, active: true },
    accountForm: { id: 0, name: '', kind: 'ASSET', openingBalance: '0.00', active: true } },
  onShow() { this.load(); },
  async load() {
    try {
      const { books, book } = await selectedBook();
      this.setData({ books, bookId: book?.id || 0, bookIndex: books.findIndex(item => item.id === book?.id), role: book?.role || '' });
      if (book) this.loadDetails();
    } catch (error) { this.toast(error); }
  },
  async loadDetails() {
    const bookId = this.data.bookId;
    try {
      const [members, categories, accounts, templates, overview] = await Promise.all([
        request(path(bookId, '/members')), request(path(bookId, '/categories')),
        request(path(bookId, '/accounts')), request(path(bookId, '/templates')),
        request(path(bookId, '/accounts/overview'))
      ]);
      if (this.data.bookId !== bookId) return;
      this.setData({ members, categories, accounts, templates,
        assetTotal: overview.assets, liabilityTotal: overview.liabilities, netAssets: overview.netAssets });
    } catch (error) { this.toast(error); }
  },
  toast(error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); },
  tab(event) { this.setData({ tab: event.currentTarget.dataset.tab }); },
  pickBook(event) { const bookIndex = Number(event.detail.value), book = this.data.books[bookIndex];
    if (!book) return; chooseBook(book.id); this.setData({ bookId: book.id, bookIndex, role: book.role }); this.loadDetails(); },
  bookName(event) { this.setData({ newBookName: event.detail.value }); },
  joinInput(event) { this.setData({ joinCode: event.detail.value }); },
  async addBook() {
    try { const result = await request('/api/finance/books', 'POST', { name: this.data.newBookName });
      chooseBook(result.id); this.setData({ newBookName: '' }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async join() {
    try { const result = await request('/api/finance/join', 'POST', { code: this.data.joinCode });
      chooseBook(result.bookId); this.setData({ joinCode: '' }); this.load(); }
    catch (error) { this.toast(error); }
  },
  async invite(event) {
    try { const result = await request(path(this.data.bookId, '/invitation'), 'POST', { role: event.currentTarget.dataset.role });
      this.setData({ inviteCode: result.code }); wx.setClipboardData({ data: result.code }); }
    catch (error) { this.toast(error); }
  },
  async memberAction(event) {
    const id = Number(event.currentTarget.dataset.id);
    wx.showActionSheet({ itemList: ['可记账', '仅查看', '移除成员'], success: async result => {
      try { await request(path(this.data.bookId, `/members/${id}`), 'PATCH', { role: ['EDITOR', 'VIEWER', 'REMOVE'][result.tapIndex] }); this.loadDetails(); }
      catch (error) { this.toast(error); }
    } });
  },
  categoryType(event) { this.setData({ categoryType: event.currentTarget.dataset.type }); },
  categoryInput(event) { this.setData({ [`categoryForm.${event.currentTarget.dataset.field}`]: event.detail.value }); },
  editCategory(event) {
    const row = this.data.categories.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (row) this.setData({ categoryType: row.type, categoryForm: { id: row.id, name: row.name, icon: row.icon, sortOrder: row.sortOrder, active: row.active } });
  },
  async saveCategory() {
    const form = this.data.categoryForm, bookId = this.data.bookId;
    try {
      await request(path(bookId, form.id ? `/categories/${form.id}` : '/categories'), form.id ? 'PUT' : 'POST', { ...form, type: this.data.categoryType });
      this.setData({ categoryForm: { id: 0, name: '', icon: '•', sortOrder: 0, active: true } }); this.loadDetails();
    } catch (error) { this.toast(error); }
  },
  async toggleCategory(event) {
    const row = this.data.categories.find(item => item.id === Number(event.currentTarget.dataset.id)); if (!row) return;
    try { await request(path(this.data.bookId, `/categories/${row.id}`), 'PUT', { name: row.name, icon: row.icon, sortOrder: row.sortOrder, active: !row.active }); this.loadDetails(); }
    catch (error) { this.toast(error); }
  },
  accountInput(event) { this.setData({ [`accountForm.${event.currentTarget.dataset.field}`]: event.detail.value }); },
  accountKind(event) { this.setData({ 'accountForm.kind': event.detail.value ? 'LIABILITY' : 'ASSET' }); },
  editAccount(event) { const row = this.data.accounts.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (row) this.setData({ accountForm: { id: row.id, name: row.name, kind: row.kind, openingBalance: row.openingBalance, active: row.active } }); },
  async saveAccount() {
    const form = this.data.accountForm;
    try { await request(path(this.data.bookId, form.id ? `/accounts/${form.id}` : '/accounts'), form.id ? 'PUT' : 'POST', form);
      this.setData({ accountForm: { id: 0, name: '', kind: 'ASSET', openingBalance: '0.00', active: true } }); this.loadDetails(); }
    catch (error) { this.toast(error); }
  },
  async toggleAccount(event) {
    const row = this.data.accounts.find(item => item.id === Number(event.currentTarget.dataset.id)); if (!row) return;
    try { await request(path(this.data.bookId, `/accounts/${row.id}`), 'PUT', { name: row.name, kind: row.kind, openingBalance: row.openingBalance, active: !row.active }); this.loadDetails(); }
    catch (error) { this.toast(error); }
  },
  async deleteTemplate(event) {
    try { await request(path(this.data.bookId, `/templates/${event.currentTarget.dataset.id}`), 'DELETE'); this.loadDetails(); }
    catch (error) { this.toast(error); }
  },
  report() { wx.navigateTo({ url: '/pages/report/index' }); }
});
