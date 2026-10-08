// API 호출 공통. 같은 출처 쿠키 세션 + Spring Security SPA CSRF(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더).

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

let csrfReady: Promise<void> | null = null

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((part) => part.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

async function ensureCsrfCookie(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) return
  csrfReady ??= fetch('/api/auth/csrf').then(() => undefined)
  await csrfReady
  csrfReady = null
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = init.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    await ensureCsrfCookie()
    headers['X-XSRF-TOKEN'] = readCookie('XSRF-TOKEN') ?? ''
  }
  if (init.body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(path, {
    method,
    headers,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  })
  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw new ApiError(
      res.status,
      data?.code ?? `HTTP_${res.status}`,
      data?.message ?? (res.status === 401 ? '로그인이 필요합니다.' : '요청을 처리하지 못했습니다.'),
    )
  }
  return data as T
}

export type SystemRole = 'USER' | 'ADMIN'
export type UserStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED'
export type GroupRole = 'OWNER' | 'MANAGER' | 'MEMBER'

export type Me = { id: string; email: string; nickname: string; systemRole: SystemRole }

export type Group = {
  id: string
  name: string
  description: string | null
  myRole: GroupRole
  memberCount: number
  createdAt: string
}

export type Member = { userId: string; nickname: string; role: GroupRole; joinedAt: string }

export type Invite = {
  id: string
  expiresAt: string
  maxUses: number
  useCount: number
  revoked: boolean
  createdAt: string
}

export type AdminUser = {
  id: string
  email: string
  nickname: string
  status: UserStatus
  systemRole: SystemRole
  loginMethod: 'EMAIL' | 'GOOGLE'
  signupNote: string | null
  createdAt: string
  reviewedAt: string | null
}

export const roleLabel: Record<GroupRole, string> = { OWNER: '방장', MANAGER: '운영진', MEMBER: '멤버' }
export const statusLabel: Record<UserStatus, string> = {
  PENDING: '승인 대기',
  ACTIVE: '활성',
  REJECTED: '거절',
  SUSPENDED: '정지',
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}
