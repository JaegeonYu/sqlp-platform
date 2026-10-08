import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { api, ApiError, type Me } from '../api'
import { useProviders } from '../auth'

const googleResultMessages: Record<string, string> = {
  EMAIL_IN_USE: '이미 이메일로 가입된 계정입니다. 이메일과 비밀번호로 로그인하세요.',
  EMAIL_NOT_VERIFIED: 'Google 계정의 이메일 인증이 완료되지 않았습니다.',
  INACTIVE: '사용할 수 없는 계정입니다. 관리자에게 문의하세요.',
  OAUTH_FAILED: 'Google 로그인에 실패했습니다. 다시 시도하세요.',
}

export function LoginPage() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const [params] = useSearchParams()
  const providers = useProviders()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(googleResultMessages[params.get('result') ?? ''] ?? null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const me = await api<Me>('/api/auth/login', { method: 'POST', body: { email, password } })
      client.setQueryData(['me'], me)
      navigate('/')
    } catch (err) {
      if (err instanceof ApiError && err.code === 'PENDING_APPROVAL') {
        navigate('/signup/pending')
        return
      }
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="narrow">
      <h1>로그인</h1>
      <div className="card">
        {providers.data?.google && (
          <>
            <a className="button secondary" style={{ width: '100%', textAlign: 'center' }} href="/oauth2/authorization/google">
              Google로 계속하기
            </a>
            <div className="divider">또는 이메일로 로그인</div>
          </>
        )}
        <form onSubmit={submit}>
          <label htmlFor="email">이메일</label>
          <input id="email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <p className="error">{error}</p>}
          <div className="row" style={{ marginTop: 16 }}>
            <button type="submit" disabled={busy}>
              로그인
            </button>
            <Link to="/signup">가입 신청</Link>
          </div>
        </form>
      </div>
      <p className="muted">가입은 관리자 승인 후 완료됩니다.</p>
    </main>
  )
}

export function SignupPage() {
  const [form, setForm] = useState({ email: '', nickname: '', password: '', passwordConfirm: '', note: '' })
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const providers = useProviders()

  const update = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [key]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (form.password !== form.passwordConfirm) {
      setError('비밀번호가 서로 다릅니다.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await api<{ message: string }>('/api/auth/signup', {
        method: 'POST',
        body: { email: form.email, nickname: form.nickname, password: form.password, note: form.note },
      })
      setDone(res.message)
    } catch (err) {
      setError(err instanceof Error ? err.message : '가입 신청에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <main className="narrow">
        <h1>가입 신청 완료</h1>
        <div className="card stack">
          <p>{done}</p>
          <Link to="/login">로그인 화면으로</Link>
        </div>
      </main>
    )
  }

  return (
    <main className="narrow">
      <h1>가입 신청</h1>
      <div className="card">
        {providers.data?.google && (
          <>
            <a className="button secondary" style={{ width: '100%', textAlign: 'center' }} href="/oauth2/authorization/google">
              Google 계정으로 신청
            </a>
            <div className="divider">또는 이메일로 신청</div>
          </>
        )}
        <form onSubmit={submit}>
          <label htmlFor="email">이메일</label>
          <input id="email" type="email" autoComplete="email" required maxLength={254} value={form.email} onChange={update('email')} />
          <label htmlFor="nickname">닉네임</label>
          <input id="nickname" required minLength={2} maxLength={40} value={form.nickname} onChange={update('nickname')} />
          <label htmlFor="password">비밀번호 (10자 이상)</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            maxLength={128}
            value={form.password}
            onChange={update('password')}
          />
          <label htmlFor="passwordConfirm">비밀번호 확인</label>
          <input
            id="passwordConfirm"
            type="password"
            autoComplete="new-password"
            required
            value={form.passwordConfirm}
            onChange={update('passwordConfirm')}
          />
          <label htmlFor="note">관리자에게 남길 말 (선택)</label>
          <textarea id="note" rows={3} maxLength={500} value={form.note} onChange={update('note')} placeholder="예: SQLP 1기 스터디원 홍길동" />
          {error && <p className="error">{error}</p>}
          <div className="row" style={{ marginTop: 16 }}>
            <button type="submit" disabled={busy}>
              신청하기
            </button>
            <Link to="/login">로그인</Link>
          </div>
        </form>
      </div>
    </main>
  )
}

export function PendingPage() {
  return (
    <main className="narrow">
      <h1>승인 대기 중</h1>
      <div className="card stack">
        <p>가입 신청이 접수되어 관리자 승인을 기다리고 있습니다. 승인되면 다시 로그인하세요.</p>
        <Link to="/login">로그인 화면으로</Link>
      </div>
    </main>
  )
}
