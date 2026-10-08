import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, formatDate, roleLabel, type Group, type GroupRole, type Invite, type Member } from '../api'
import { useMe } from '../auth'

function errorText(error: unknown): string | null {
  return error instanceof Error ? error.message : null
}

export function HomePage() {
  const client = useQueryClient()
  const navigate = useNavigate()
  const groups = useQuery({ queryKey: ['groups'], queryFn: () => api<Group[]>('/api/groups') })
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const create = useMutation({
    mutationFn: () => api<Group>('/api/groups', { method: 'POST', body: { name, description } }),
    onSuccess: (group) => {
      client.invalidateQueries({ queryKey: ['groups'] })
      navigate(`/groups/${group.id}`)
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  return (
    <main>
      <h1>내 스터디 그룹</h1>
      <div className="card">
        {groups.isPending && <p className="muted">불러오는 중…</p>}
        {groups.data?.length === 0 && <p className="muted">아직 속한 그룹이 없습니다. 초대 링크를 받거나 새 그룹을 만드세요.</p>}
        <ul className="list">
          {groups.data?.map((g) => (
            <li key={g.id}>
              <Link className="grow" to={`/groups/${g.id}`}>
                {g.name}
              </Link>
              <span className="badge">{roleLabel[g.myRole]}</span>
              <span className="muted">{g.memberCount}명</span>
            </li>
          ))}
        </ul>
      </div>
      <div className="card">
        <h2>새 그룹 만들기</h2>
        <form onSubmit={submit}>
          <label htmlFor="name">이름</label>
          <input id="name" required maxLength={60} value={name} onChange={(e) => setName(e.target.value)} placeholder="예: SQLP 1기" />
          <label htmlFor="desc">소개 (선택)</label>
          <textarea id="desc" rows={2} maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
          {create.error && <p className="error">{errorText(create.error)}</p>}
          <div className="row" style={{ marginTop: 12 }}>
            <button type="submit" disabled={create.isPending}>
              만들기
            </button>
          </div>
        </form>
      </div>
    </main>
  )
}

export function GroupPage() {
  const { groupId = '' } = useParams()
  const me = useMe()
  const client = useQueryClient()
  const navigate = useNavigate()
  const group = useQuery({ queryKey: ['group', groupId], queryFn: () => api<Group>(`/api/groups/${groupId}`) })
  const members = useQuery({
    queryKey: ['group', groupId, 'members'],
    queryFn: () => api<Member[]>(`/api/groups/${groupId}/members`),
    enabled: group.isSuccess,
  })
  const refresh = () => client.invalidateQueries({ queryKey: ['group', groupId] })

  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: GroupRole }) =>
      api(`/api/groups/${groupId}/members/${userId}`, { method: 'PATCH', body: { role } }),
    onSuccess: refresh,
  })
  const remove = useMutation({
    mutationFn: (userId: string) => api(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: (_data, userId) => {
      if (userId === me.data?.id) {
        client.invalidateQueries({ queryKey: ['groups'] })
        navigate('/')
      } else {
        refresh()
      }
    },
  })

  if (group.error) {
    return (
      <main>
        <p className="error">{errorText(group.error)}</p>
        <Link to="/">내 그룹으로</Link>
      </main>
    )
  }
  if (!group.data) return <main className="muted">불러오는 중…</main>

  const myRole = group.data.myRole
  const canManage = myRole === 'OWNER' || myRole === 'MANAGER'

  function canRemove(target: Member): boolean {
    if (target.userId === me.data?.id) return false
    return myRole === 'OWNER' || (myRole === 'MANAGER' && target.role === 'MEMBER')
  }

  return (
    <main>
      <h1>{group.data.name}</h1>
      {group.data.description && <p className="muted">{group.data.description}</p>}

      <div className="card">
        <h2>멤버 {group.data.memberCount}명</h2>
        <ul className="list">
          {members.data?.map((m) => (
            <li key={m.userId}>
              <span className="grow">
                {m.nickname}
                {m.userId === me.data?.id && <span className="muted"> (나)</span>}
              </span>
              {myRole === 'OWNER' && m.role !== 'OWNER' ? (
                <select
                  style={{ width: 'auto' }}
                  value={m.role}
                  onChange={(e) => changeRole.mutate({ userId: m.userId, role: e.target.value as GroupRole })}
                >
                  <option value="MANAGER">{roleLabel.MANAGER}</option>
                  <option value="MEMBER">{roleLabel.MEMBER}</option>
                </select>
              ) : (
                <span className="badge">{roleLabel[m.role]}</span>
              )}
              {canRemove(m) && (
                <button
                  className="danger"
                  onClick={() => window.confirm(`${m.nickname} 님을 내보낼까요?`) && remove.mutate(m.userId)}
                >
                  내보내기
                </button>
              )}
            </li>
          ))}
        </ul>
        {(changeRole.error || remove.error) && <p className="error">{errorText(changeRole.error ?? remove.error)}</p>}
      </div>

      {canManage && <InviteCard groupId={groupId} />}

      {myRole !== 'OWNER' && me.data && (
        <button className="danger" onClick={() => window.confirm('그룹을 떠날까요?') && remove.mutate(me.data!.id)}>
          그룹 떠나기
        </button>
      )}
    </main>
  )
}

function InviteCard({ groupId }: { groupId: string }) {
  const client = useQueryClient()
  const [days, setDays] = useState(7)
  const [maxUses, setMaxUses] = useState(10)
  const [link, setLink] = useState<string | null>(null)
  const invites = useQuery({
    queryKey: ['group', groupId, 'invites'],
    queryFn: () => api<Invite[]>(`/api/groups/${groupId}/invites`),
  })
  const create = useMutation({
    mutationFn: () =>
      api<{ invite: Invite; token: string }>(`/api/groups/${groupId}/invites`, {
        method: 'POST',
        body: { expiresInDays: days, maxUses },
      }),
    onSuccess: (res) => {
      // 토큰은 URL 조각(#)에 넣어 서버 접근 로그에 남지 않게 한다
      setLink(`${window.location.origin}/invite#${res.token}`)
      client.invalidateQueries({ queryKey: ['group', groupId, 'invites'] })
    },
  })
  const revoke = useMutation({
    mutationFn: (inviteId: string) => api(`/api/groups/${groupId}/invites/${inviteId}`, { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['group', groupId, 'invites'] }),
  })

  const now = Date.now()

  return (
    <div className="card">
      <h2>초대 링크</h2>
      <div className="row">
        <label className="row">
          유효 기간
          <select style={{ width: 'auto' }} value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[1, 3, 7, 14, 30].map((d) => (
              <option key={d} value={d}>
                {d}일
              </option>
            ))}
          </select>
        </label>
        <label className="row">
          최대 사용
          <input style={{ width: 80 }} type="number" min={1} max={100} value={maxUses} onChange={(e) => setMaxUses(Number(e.target.value))} />
          회
        </label>
        <button onClick={() => create.mutate()} disabled={create.isPending}>
          링크 만들기
        </button>
      </div>
      {create.error && <p className="error">{errorText(create.error)}</p>}
      {link && (
        <div className="stack" style={{ marginTop: 12 }}>
          <p className="notice">이 링크는 지금 한 번만 표시됩니다. 복사해서 전달하세요.</p>
          <code className="token">{link}</code>
          <button className="secondary" onClick={() => navigator.clipboard.writeText(link)}>
            복사
          </button>
        </div>
      )}
      <ul className="list" style={{ marginTop: 12 }}>
        {invites.data?.map((i) => {
          const usable = !i.revoked && i.useCount < i.maxUses && new Date(i.expiresAt).getTime() > now
          return (
            <li key={i.id}>
              <span className="grow muted">
                {formatDate(i.createdAt)} 생성 · {formatDate(i.expiresAt)} 만료 · {i.useCount}/{i.maxUses}회 사용
              </span>
              {usable ? (
                <button className="danger" onClick={() => revoke.mutate(i.id)}>
                  폐기
                </button>
              ) : (
                <span className="badge">{i.revoked ? '폐기됨' : '사용 불가'}</span>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}

export function InvitePage() {
  const token = window.location.hash.slice(1)
  const navigate = useNavigate()
  const client = useQueryClient()
  const preview = useQuery({
    queryKey: ['invite', token],
    queryFn: () =>
      api<{ groupId: string; groupName: string; description: string | null; alreadyMember: boolean }>(
        '/api/invites/preview',
        { method: 'POST', body: { token } },
      ),
    enabled: token.length > 0,
    retry: false,
  })
  const accept = useMutation({
    mutationFn: () => api<{ groupId: string }>('/api/invites/accept', { method: 'POST', body: { token } }),
    onSuccess: (res) => {
      client.invalidateQueries({ queryKey: ['groups'] })
      navigate(`/groups/${res.groupId}`)
    },
  })

  return (
    <main className="narrow">
      <h1>스터디 초대</h1>
      <div className="card stack">
        {!token && <p className="error">초대 링크가 올바르지 않습니다.</p>}
        {preview.error && <p className="error">{errorText(preview.error)}</p>}
        {preview.data && (
          <>
            <p>
              <strong>{preview.data.groupName}</strong> 그룹에 초대되었습니다.
            </p>
            {preview.data.description && <p className="muted">{preview.data.description}</p>}
            {preview.data.alreadyMember ? (
              <Link to={`/groups/${preview.data.groupId}`}>이미 멤버입니다. 그룹으로 이동</Link>
            ) : (
              <button onClick={() => accept.mutate()} disabled={accept.isPending}>
                참여하기
              </button>
            )}
            {accept.error && <p className="error">{errorText(accept.error)}</p>}
          </>
        )}
      </div>
    </main>
  )
}
