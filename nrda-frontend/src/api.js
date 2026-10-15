async function request(path, options = {}) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 12000)
  try {
    const response = await fetch(`/api/dashboard${path}`, {
      ...options,
      signal: controller.signal,
      headers: { 'Accept': 'application/json', ...options.headers },
    })
    if (!response.ok) {
      let message = `HTTP ${response.status}`
      try {
        const data = await response.json()
        message = data.message || data.detail || data.error || message
      } catch { /* server did not return JSON */ }
      throw new Error(message)
    }
    return response.json()
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('Dashboard API timed out')
    throw error
  } finally {
    clearTimeout(timeout)
  }
}

export const dashboardApi = {
  summary: () => request('/summary'),
  services: () => request('/services'),
  analytics: () => request('/analytics'),
  experiments: (limit = 100) => request(`/experiments?limit=${limit}`),
  setSlowFault: (state) => request(`/faults/slow/${state}`, { method: 'POST' }),
}
