import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}))

vi.mock('@/app/router', () => ({
  default: {
    currentRoute: { value: { path: '/chat', fullPath: '/chat' } },
    replace: vi.fn(() => Promise.resolve())
  }
}))

import router from '@/app/router'
import { AuthError, request } from '@/shared/api/request'
import { loadRefreshTokenFromStore } from '@/shared/utils/auth'

function jsonResponse(body, status = 200) {
  return {
    status,
    ok: status >= 200 && status < 300,
    json: async () => body
  }
}

function fakeJwt(expOffsetSeconds) {
  const payload = {
    exp: Math.floor(Date.now() / 1000) + expOffsetSeconds
  }
  return `header.${btoa(JSON.stringify(payload))}.signature`
}

describe('request refresh & retry', () => {
  beforeEach(async () => {
    localStorage.clear()
    await loadRefreshTokenFromStore()
    vi.unstubAllGlobals()
  })

  it('401 后刷新一次并重放原请求', async () => {
    const oldAccess = fakeJwt(3600)
    localStorage.setItem('token', oldAccess)
    localStorage.setItem('refreshToken', 'refresh-1')

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: '401', msg: '登录已过期' }, 401))
      .mockResolvedValueOnce(
        jsonResponse({
          code: '200',
          data: { token: 'access-new', refreshToken: 'refresh-2' }
        })
      )
      .mockResolvedValueOnce(jsonResponse({ code: '200', data: { ok: true } }))

    vi.stubGlobal('fetch', fetchMock)

    const result = await request('/api/tasks/demo')

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1][0]).toContain('/user/refresh')
    expect(result).toEqual({ code: '200', data: { ok: true } })

    const retriedHeaders = fetchMock.mock.calls[2][1].headers
    expect(retriedHeaders.Authorization).toBe('Bearer access-new')
  })

  it('刷新失败时清理登录态并跳转登录页', async () => {
    localStorage.setItem('token', fakeJwt(3600))
    localStorage.setItem('refreshToken', 'expired-refresh')

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: '401', msg: '登录已过期' }, 401))
      .mockResolvedValueOnce(jsonResponse({ code: '401', msg: '登录已过期' }, 401))

    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/api/tasks/demo')).rejects.toBeInstanceOf(AuthError)

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(router.replace).toHaveBeenCalled()
  })
})
