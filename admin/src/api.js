const tokenKey = 'bookkeeping_admin_token'

export const token = () => sessionStorage.getItem(tokenKey)
export const saveToken = value => sessionStorage.setItem(tokenKey, value)
export const clearToken = () => sessionStorage.removeItem(tokenKey)

export async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers }
  if (token()) headers.Authorization = `Bearer ${token()}`
  const response = await fetch(`/api/admin${path}`, { ...options, headers })
  if (response.status === 401 && path !== '/session') {
    clearToken()
    window.dispatchEvent(new Event('admin-session-expired'))
  }
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.error || `请求失败 (${response.status})`)
  return body
}

export function query(values) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== '' && value !== undefined && value !== null) params.set(key, value)
  }
  return params.toString()
}

export async function download(path, filename) {
  const response = await fetch(`/api/admin${path}`, { headers: { Authorization: `Bearer ${token()}` } })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.error || '导出失败')
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = filename; link.click()
  URL.revokeObjectURL(url)
}
