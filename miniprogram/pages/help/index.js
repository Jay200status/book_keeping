const { request } = require('../../utils/api');
Page({
  data: { announcements: [], faqs: [], versions: [], privacy: [], terms: [], loading: true },
  async onShow() {
    try { const items = await request('/api/content');
      this.setData({ announcements: items.filter(i => i.kind === 'ANNOUNCEMENT'), faqs: items.filter(i => i.kind === 'FAQ'),
        versions: items.filter(i => i.kind === 'VERSION'), privacy: items.filter(i => i.kind === 'PRIVACY'),
        terms: items.filter(i => i.kind === 'TERMS'), loading: false }); }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }); }
  }
});
