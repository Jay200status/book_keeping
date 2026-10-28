const { request } = require('../../utils/api');
Page({
  data: { displayName: '记账用户', officialBound: false, code: '', expires: '', loading: true, editing: false, draftName: '', reminderAvailable: false, reminderTemplateId: '' },
  onShow() { if (this.getTabBar()) this.getTabBar().setData({ selected: 2 }); this.load(); },
  async load() {
    try {
      const [me, subscription] = await Promise.all([request('/api/me'), request('/api/subscription-config')]);
      this.setData({ ...me, reminderAvailable: Boolean(subscription.templateId), reminderTemplateId: subscription.templateId, loading: false });
    }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }); }
  },
  editName() { this.setData({ editing: true, draftName: this.data.displayName === '记账用户' ? '' : this.data.displayName }); },
  nameInput(event) { this.setData({ draftName: event.detail.value }); },
  async saveName() {
    try { const me = await request('/api/me', 'POST', { displayName: this.data.draftName }); this.setData({ displayName: me.displayName, editing: false }); }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  async makeCode() {
    try {
      const result = await request('/api/binding-code', 'POST', {});
      const date = new Date(result.expiresAt);
      this.setData({ code: result.code, expires: `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}` });
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
  },
  copyCode() { wx.setClipboardData({ data: `绑定 ${this.data.code}` }); },
  openManage() { wx.navigateTo({ url: '/pages/manage/index' }); },
  openReport() { wx.navigateTo({ url: '/pages/report/index' }); },
  openFeedback() { wx.navigateTo({ url: '/pages/feedback/index' }); },
  openHelp() { wx.navigateTo({ url: '/pages/help/index' }); },
  openPlanning() { wx.navigateTo({ url: '/pages/planning/index' }); },
  async subscribeReminders() {
    try {
      const templateId = this.data.reminderTemplateId;
      if (!templateId) throw new Error('提醒模板尚未配置');
      const result = await new Promise((resolve, reject) => wx.requestSubscribeMessage({ tmplIds: [templateId], success: resolve, fail: reject }));
      if (result[templateId] !== 'accept') throw new Error('未授权接收提醒');
      await request('/api/subscription-grant', 'POST', {});
      wx.showToast({ title: '已订阅一次提醒', icon: 'success' });
    } catch (error) { wx.showToast({ title: error.message || '订阅失败', icon: 'none' }); }
  }
});
