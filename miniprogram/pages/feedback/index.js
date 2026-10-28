const { request } = require('../../utils/api');
Page({
  data: { subject: '', content: '', items: [], saving: false },
  onShow() { this.load(); },
  async load() { try { this.setData({ items: await request('/api/feedback') }); }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }); } },
  subject(event) { this.setData({ subject: event.detail.value }); },
  content(event) { this.setData({ content: event.detail.value }); },
  async send() {
    this.setData({ saving: true });
    try { await request('/api/feedback', 'POST', { subject: this.data.subject, content: this.data.content });
      this.setData({ subject: '', content: '' }); wx.showToast({ title: '反馈已提交', icon: 'success' }); this.load(); }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
    finally { this.setData({ saving: false }); }
  }
});
