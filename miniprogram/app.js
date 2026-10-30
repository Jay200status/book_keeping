const { API_BASE } = require('./config');

App({
  //取出本地缓存的token
  globalData: { token: wx.getStorageSync('token') || '' },
  async ensureSession() {
    if (this.globalData.token) return this.globalData.token;
    //sessionPromise记录当前是否正在登录
    if (this.sessionPromise) return this.sessionPromise;
    this.sessionPromise = new Promise((resolve, reject) => {
      //获取code 有效期：5分钟
      wx.login({ success: result => result.code ? resolve(result.code) : reject(new Error('微信身份获取失败')), fail: reject });
    }).then(code => new Promise((resolve, reject) => {
      wx.request({
        url: `${API_BASE}/api/session`, method: 'POST', data: { code, deviceInfo: (() => {
          try { const info = wx.getSystemInfoSync(); return `${info.brand || ''} ${info.model || ''} · ${info.system || ''}`; }
          catch { return ''; }
        })() },
        success: result => result.statusCode === 200 ? resolve(result.data.token) : reject(new Error(result.data.error || '身份建立失败')),
        fail: reject
      });
    })).then(token => {
      this.globalData.token = token; wx.setStorageSync('token', token); return token;
    }).finally(() => { this.sessionPromise = null; });
    return this.sessionPromise;
  }
});
