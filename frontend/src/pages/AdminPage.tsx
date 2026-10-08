import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, formatDate, statusLabel, type AdminUser, type UserStatus } from '../api'
import { useMe } from '../auth'

const filters: (UserStatus | 'ALL')[] = ['PENDING', 'ACTIVE', 'SUSPENDED', 'REJECTED', 'ALL']

export function AdminPage() {
  const me = useMe()
  const client = useQueryClient()
  const [filter, setFilter] = useState<UserStatus | 'ALL'>('PENDING')
  const users = useQuery({
    queryKey: ['admin', 'users', filter],
    queryFn: () => api<AdminUser[]>(filter === 'ALL' ? '/api/admin/users' : `/api/admin/users?status=${filter}`),
  })
  const change = useMutation({
    mutationFn: ({ id, status }: { id: string; status: UserStatus }) =>
      api(`/api/admin/users/${id}/status`, { method: 'POST', body: { status } }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  return (
    <main>
      <h1>회원 관리</h1>
      <div className="row" style={{ marginBottom: 12 }}>
        {filters.map((f) => (
          <button key={f} className={f === filter ? '' : 'secondary'} onClick={() => setFilter(f)}>
            {f === 'ALL' ? '전체' : statusLabel[f]}
          </button>
        ))}
      </div>
      <div className="card">
        {users.data?.length === 0 && <p className="muted">해당하는 회원이 없습니다.</p>}
        <ul className="list">
          {users.data?.map((u) => (
            <li key={u.id}>
              <div className="grow">
                <div>
                  {u.nickname} <span className="muted">{u.email}</span>
                </div>
                <div className="muted">
                  {u.loginMethod === 'GOOGLE' ? 'Google' : '이메일'} · {formatDate(u.createdAt)} 신청
                  {u.systemRole === 'ADMIN' && ' · 관리자'}
                </div>
                {u.signupNote && <div className="muted">“{u.signupNote}”</div>}
              </div>
              <span className="badge">{statusLabel[u.status]}</span>
              {u.id !== me.data?.id && (
                <div className="row">
                  {u.status !== 'ACTIVE' && (
                    <button onClick={() => change.mutate({ id: u.id, status: 'ACTIVE' })}>
                      {u.status === 'PENDING' ? '승인' : '활성화'}
                    </button>
                  )}
                  {u.status === 'PENDING' && (
                    <button className="danger" onClick={() => change.mutate({ id: u.id, status: 'REJECTED' })}>
                      거절
                    </button>
                  )}
                  {u.status === 'ACTIVE' && (
                    <button
                      className="danger"
                      onClick={() => window.confirm(`${u.nickname} 님을 정지할까요?`) && change.mutate({ id: u.id, status: 'SUSPENDED' })}
                    >
                      정지
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
        {change.error && <p className="error">{change.error.message}</p>}
      </div>
    </main>
  )
}
