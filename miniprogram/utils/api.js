const { API_BASE } = require('../config');

async function request(path, method = 'GET', data, retried = false) {
  //app.js中的App({...})会创建全局唯一实例
  //getApp()的作用是在任意地方取到这个实例
  const app = getApp();
  const token = await app.ensureSession();
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${API_BASE}${path}`, method, data,
      header: { Authorization: `Bearer ${token}` },
      success(result) {
        if (result.statusCode >= 200 && result.statusCode < 300) return resolve(result.data);
        if (result.statusCode === 401) { app.globalData.token = ''; wx.removeStorageSync('token'); }
        reject(new Error(result.data?.error || '请求失败'));
      }, fail: reject
    });
  }).catch(error => {
    if (!retried && error.message === '请重新打开小程序') return request(path, method, data, true);
    throw error;
  });
}
module.exports = { request };
