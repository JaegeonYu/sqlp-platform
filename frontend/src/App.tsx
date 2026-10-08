import type { ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation } from 'react-router'
import { useLogout, useMe } from './auth'
import { LoginPage, PendingPage, SignupPage } from './pages/AuthPages'
import { GroupPage, HomePage, InvitePage } from './pages/GroupPages'
import { AdminPage } from './pages/AdminPage'

function TopBar() {
  const me = useMe()
  const logout = useLogout()
  return (
    <header className="topbar">
      <Link className="brand" to="/">
        SQLP Study
      </Link>
      <span className="spacer" />
      {me.data && (
        <>
          {me.data.systemRole === 'ADMIN' && <Link to="/admin/users">회원 관리</Link>}
          <span className="muted">{me.data.nickname}</span>
          <button className="secondary" onClick={logout}>
            로그아웃
          </button>
        </>
      )}
    </header>
  )
}

function RequireLogin({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const me = useMe()
  const location = useLocation()
  if (me.isPending) return <main className="muted">불러오는 중…</main>
  if (!me.data) {
    // 초대 링크(#토큰)는 로그인 후 다시 열 수 있도록 보존한다
    sessionStorageSafeSet('afterLogin', location.pathname + location.hash)
    return <Navigate to="/login" replace />
  }
  if (admin && me.data.systemRole !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}

function sessionStorageSafeSet(key: string, value: string) {
  try {
    sessionStorage.setItem(key, value)
  } catch {
    // 저장소를 쓸 수 없으면 무시한다
  }
}

function AfterLoginRedirect() {
  let target = '/'
  try {
    target = sessionStorage.getItem('afterLogin') ?? '/'
    sessionStorage.removeItem('afterLogin')
  } catch {
    // 무시
  }
  // 같은 사이트 경로만 허용(오픈 리다이렉트 방지)
  if (!target.startsWith('/') || target.startsWith('//')) target = '/'
  if (target.startsWith('/invite')) {
    window.location.replace(target)
    return null
  }
  return target === '/' ? <HomePage /> : <Navigate to={target} replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <TopBar />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/signup/pending" element={<PendingPage />} />
        <Route
          path="/"
          element={
            <RequireLogin>
              <AfterLoginRedirect />
            </RequireLogin>
          }
        />
        <Route
          path="/groups/:groupId"
          element={
            <RequireLogin>
              <GroupPage />
            </RequireLogin>
          }
        />
        <Route
          path="/invite"
          element={
            <RequireLogin>
              <InvitePage />
            </RequireLogin>
          }
        />
        <Route
          path="/admin/users"
          element={
            <RequireLogin admin>
              <AdminPage />
            </RequireLogin>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
