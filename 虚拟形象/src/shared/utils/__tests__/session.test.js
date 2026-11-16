import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadRefreshTokenFromStore } from '@/shared/utils/auth'
import { getValidAccessToken, refreshAccessToken } from '@/shared/utils/session'

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

describe('session refresh', () => {
  beforeEach(async () => {
    localStorage.clear()
    await loadRefreshTokenFromStore()
    vi.unstubAllGlobals()
  })

  it('并发刷新共享同一个请求（single-flight）', async () => {
    localStorage.setItem('refreshToken', 'refresh-1')
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        code: '200',
        data: { token: 'access-2', refreshToken: 'refresh-2' }
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const [first, second] = await Promise.all([
      refreshAccessToken(),
      refreshAccessToken()
    ])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(first).toBe('access-2')
    expect(second).toBe('access-2')
    expect(localStorage.getItem('token')).toBe('access-2')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-2')
  })

  it('Refresh Token 失效时抛出 authFailed', async () => {
    localStorage.setItem('refreshToken', 'expired-refresh')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: '401', msg: '登录已过期' }, 401))
    )

    await expect(refreshAccessToken()).rejects.toMatchObject({
      name: 'RefreshError',
      authFailed: true
    })
  })

  it('Access Token 未临期时直接返回，不请求刷新', async () => {
    const accessToken = fakeJwt(3600)
    localStorage.setItem('token', accessToken)
    localStorage.setItem('refreshToken', 'refresh-1')
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(getValidAccessToken()).resolves.toBe(accessToken)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('Access Token 已过期时自动刷新', async () => {
    localStorage.setItem('token', fakeJwt(-60))
    localStorage.setItem('refreshToken', 'refresh-1')
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        code: '200',
        data: { token: 'access-new', refreshToken: 'refresh-new' }
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(getValidAccessToken()).resolves.toBe('access-new')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
