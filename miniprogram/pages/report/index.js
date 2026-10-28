const { request } = require('../../utils/api');
const { selectedBook, path, today } = require('../../utils/finance');
Page({
  data: { mode: 'month', month: today().slice(0, 7), year: Number(today().slice(0, 4)),
    bookId: 0, bookName: '', income: '0.00', expense: '0.00', categories: [], months: [], calendarDays: [], weekdays: ['一','二','三','四','五','六','日'], loading: true },
  async onShow() {
    try { const { book } = await selectedBook(); if (!book) return;
      this.setData({ bookId: book.id, bookName: book.name }); this.load(); }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  setMode(event) { this.setData({ mode: event.currentTarget.dataset.mode }); this.load(); },
  pickMonth(event) { this.setData({ month: event.detail.value }); this.load(); },
  pickYear(event) { this.setData({ year: Number(event.detail.value) }); this.load(); },
  async load() {
    const { bookId, mode, month, year } = this.data;
    if (!bookId) return;
    this.setData({ loading: true });
    try {
      if (mode === 'calendar') {
        const rows = await request(path(bookId, `/calendar?month=${month}`));
        const [yearValue, monthValue] = month.split('-').map(Number);
        const firstWeekday = (new Date(yearValue, monthValue - 1, 1).getDay() + 6) % 7;
        const count = new Date(yearValue, monthValue, 0).getDate();
        const cells = Array.from({ length: firstWeekday }, (_, i) => ({ key: `before-${i}`, blank: true }));
        for (let day = 1; day <= count; day++) {
          const date = `${month}-${String(day).padStart(2, '0')}`;
          cells.push({ key: date, day,
            income: rows.find(item => item.date === date && item.type === 'INCOME')?.amount || '',
            expense: rows.find(item => item.date === date && item.type === 'EXPENSE')?.amount || '' });
        }
        while (cells.length % 7) cells.push({ key: `after-${cells.length}`, blank: true });
        this.setData({ calendarDays: cells, loading: false });
      } else if (mode === 'month') {
        const report = await request(path(bookId, `/report?month=${month}`));
        const total = type => report.totals.find(item => item.type === type)?.amount || '0.00';
        const expense = Number(total('EXPENSE'));
        this.setData({ income: total('INCOME'), expense: total('EXPENSE'), categories: report.categories.map(row => ({ ...row,
          percent: expense ? Math.round(Number(row.amount) / expense * 100) : 0 })), months: [], loading: false });
      } else {
        const report = await request(path(bookId, `/annual-report?year=${year}`));
        const sum = type => report.months.filter(item => item.type === type).reduce((n, item) => n + Number(item.amount), 0).toFixed(2);
        const expense = Number(sum('EXPENSE'));
        const months = Array.from({ length: 12 }, (_, i) => ({ month: i + 1,
          expense: report.months.find(item => item.month === i + 1 && item.type === 'EXPENSE')?.amount || '0.00',
          income: report.months.find(item => item.month === i + 1 && item.type === 'INCOME')?.amount || '0.00' }));
        this.setData({ income: sum('INCOME'), expense: sum('EXPENSE'), months,
          categories: report.categories.map(row => ({ ...row, percent: expense ? Math.round(Number(row.amount) / expense * 100) : 0 })), loading: false });
      }
    } catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }); }
  }
});
