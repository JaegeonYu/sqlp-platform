import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, cohortStatusLabel, type CohortDetail, type CohortStatus, type SessionRow } from '../api'
import { CohortAssignments } from './SubmissionPages'

function errorText(error: unknown): string | null {
  return error instanceof Error ? error.message : null
}

/** ISO 시각 → datetime-local 입력값(브라우저 시간대) */
function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatSession(iso: string | null): string {
  if (!iso) return '미정'
  return new Date(iso).toLocaleString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit' })
}

export function CohortPage() {
  const { cohortId = '' } = useParams()
  const client = useQueryClient()
  const cohort = useQuery({ queryKey: ['cohort', cohortId], queryFn: () => api<CohortDetail>(`/api/cohorts/${cohortId}`) })
  const refresh = () => client.invalidateQueries({ queryKey: ['cohort', cohortId] })
  const updateStatus = useMutation({
    mutationFn: (status: CohortStatus) =>
      api(`/api/cohorts/${cohortId}`, { method: 'PATCH', body: { name: cohort.data!.name, status } }),
    onSuccess: refresh,
  })

  if (cohort.error) return <main className="error">{errorText(cohort.error)}</main>
  if (!cohort.data) return <main className="muted">불러오는 중…</main>
  const c = cohort.data

  return (
    <main>
      <p className="muted">
        <Link to={`/groups/${c.groupId}`}>← 그룹으로</Link>
      </p>
      <div className="card">
        <div className="row">
          <h1 className="grow" style={{ margin: 0 }}>
            {c.name}
          </h1>
          {c.canManage ? (
            <select style={{ width: 'auto' }} value={c.status} onChange={(e) => updateStatus.mutate(e.target.value as CohortStatus)}>
              {(Object.keys(cohortStatusLabel) as CohortStatus[]).map((s) => (
                <option key={s} value={s}>
                  {cohortStatusLabel[s]}
                </option>
              ))}
            </select>
          ) : (
            <span className="badge">{cohortStatusLabel[c.status]}</span>
          )}
        </div>
        <p className="muted">
          코스 <Link to={`/courses/${c.course.id}`}>{c.course.title}</Link> (『{c.course.bookTitle}』 v{c.course.versionNo}) · {c.startsOn} 시작
        </p>
      </div>

      <CohortAssignments cohortId={cohortId} />

      <MonthCalendar schedule={c.schedule} />

      <div className="card">
        <h2>장별 일정</h2>
        <ul className="list">
          {c.schedule.map((row) =>
            c.canManage ? (
              <SessionEditor key={row.chapterId} cohortId={cohortId} row={row} members={c.members} onSaved={refresh} />
            ) : (
              <li key={row.chapterId}>
                <span className="grow">
                  {row.position}. {row.chapterTitle}
                </span>
                <span className="muted">{formatSession(row.scheduledAt)}</span>
                <span className="badge">{row.presenterNickname ?? '발표자 미정'}</span>
                {row.note && <span className="muted">{row.note}</span>}
              </li>
            ),
          )}
        </ul>
        {c.schedule.length === 0 && <p className="muted">코스에 장이 없습니다. 코스에 장을 추가하면 여기에 나타납니다.</p>}
      </div>
    </main>
  )
}

function SessionEditor(props: {
  cohortId: string
  row: SessionRow
  members: CohortDetail['members']
  onSaved: () => void
}) {
  const { cohortId, row, members, onSaved } = props
  const [when, setWhen] = useState(toLocalInput(row.scheduledAt))
  const [presenterId, setPresenterId] = useState(row.presenterId ?? '')
  const [note, setNote] = useState(row.note ?? '')
  const save = useMutation({
    mutationFn: () =>
      api(`/api/cohorts/${cohortId}/sessions/${row.chapterId}`, {
        method: 'PUT',
        body: {
          scheduledAt: when ? new Date(when).toISOString() : null,
          presenterId: presenterId || null,
          note,
        },
      }),
    onSuccess: onSaved,
  })
  const dirty =
    when !== toLocalInput(row.scheduledAt) || presenterId !== (row.presenterId ?? '') || note !== (row.note ?? '')

  return (
    <li>
      <span className="grow" style={{ minWidth: 160 }}>
        {row.position}. {row.chapterTitle}
      </span>
      <input style={{ width: 'auto' }} type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
      <select style={{ width: 'auto' }} value={presenterId} onChange={(e) => setPresenterId(e.target.value)}>
        <option value="">발표자 미정</option>
        {members.map((m) => (
          <option key={m.userId} value={m.userId}>
            {m.nickname}
          </option>
        ))}
      </select>
      <input style={{ width: 160 }} maxLength={500} placeholder="메모" value={note} onChange={(e) => setNote(e.target.value)} />
      <button className="secondary" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
        저장
      </button>
      {save.error && <span className="error">{errorText(save.error)}</span>}
    </li>
  )
}

const weekdays = ['일', '월', '화', '수', '목', '금', '토']

function MonthCalendar({ schedule }: { schedule: SessionRow[] }) {
  const dated = schedule.filter((s) => s.scheduledAt)
  // 처음 보여 줄 달: 다가오는 일정이 있는 달, 없으면 첫 일정의 달
  const [month, setMonth] = useState(() => {
    const now = new Date()
    const upcoming = dated.map((s) => new Date(s.scheduledAt!)).find((d) => d >= now) ?? (dated[0] ? new Date(dated[0].scheduledAt!) : now)
    return new Date(upcoming.getFullYear(), upcoming.getMonth(), 1)
  })

  const year = month.getFullYear()
  const m = month.getMonth()
  const firstWeekday = new Date(year, m, 1).getDay()
  const days = new Date(year, m + 1, 0).getDate()
  const cells: (number | null)[] = [...Array(firstWeekday).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)]
  while (cells.length % 7 !== 0) cells.push(null)

  const byDay = new Map<number, SessionRow[]>()
  for (const s of dated) {
    const d = new Date(s.scheduledAt!)
    if (d.getFullYear() === year && d.getMonth() === m) {
      byDay.set(d.getDate(), [...(byDay.get(d.getDate()) ?? []), s])
    }
  }

  return (
    <div className="card">
      <div className="row" style={{ marginBottom: 8 }}>
        <button className="secondary" onClick={() => setMonth(new Date(year, m - 1, 1))} aria-label="이전 달">
          ‹
        </button>
        <h2 className="grow" style={{ margin: 0, textAlign: 'center' }}>
          {year}년 {m + 1}월
        </h2>
        <button className="secondary" onClick={() => setMonth(new Date(year, m + 1, 1))} aria-label="다음 달">
          ›
        </button>
      </div>
      <div className="calendar">
        {weekdays.map((w) => (
          <div key={w} className="calendar-head">
            {w}
          </div>
        ))}
        {cells.map((day, i) => (
          <div key={i} className={day ? 'calendar-cell' : 'calendar-cell empty'}>
            {day && <div className="calendar-day">{day}</div>}
            {day &&
              byDay.get(day)?.map((s) => (
                <div key={s.chapterId} className="calendar-event" title={s.chapterTitle}>
                  {new Date(s.scheduledAt!).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })} {s.position}장
                  {s.presenterNickname && ` · ${s.presenterNickname}`}
                </div>
              ))}
          </div>
        ))}
      </div>
    </div>
  )
}
