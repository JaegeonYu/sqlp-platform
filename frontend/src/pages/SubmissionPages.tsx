import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api,
  formatDate,
  submissionStatusLabel,
  type AssignmentRow,
  type Progress,
  type ReviewComment,
  type SubmissionDetail,
  type SubmissionList,
  type SubmissionStatus,
} from '../api'
import { Markdown } from '../Markdown'

function errorText(error: unknown): string | null {
  return error instanceof Error ? error.message : null
}

export function StatusBadge({ status, late }: { status: SubmissionStatus | null; late?: boolean }) {
  return (
    <span className={`badge status-${status ?? 'NONE'}`}>
      {status ? submissionStatusLabel[status] : '미제출'}
      {late && ' · 지각'}
    </span>
  )
}

function dueText(dueAt: string | null): string {
  return dueAt ? `마감 ${formatDate(dueAt)}` : '마감 미정'
}

/** 기수 화면에 들어가는 과제 목록과 진척도 */
export function CohortAssignments({ cohortId }: { cohortId: string }) {
  const assignments = useQuery({
    queryKey: ['cohort', cohortId, 'assignments'],
    queryFn: () => api<AssignmentRow[]>(`/api/cohorts/${cohortId}/assignments`),
  })
  const progress = useQuery({
    queryKey: ['cohort', cohortId, 'progress'],
    queryFn: () => api<Progress>(`/api/cohorts/${cohortId}/progress`),
  })

  return (
    <>
      <div className="card">
        <h2>과제</h2>
        {assignments.data?.length === 0 && <p className="muted">코스에 과제 항목이 없습니다.</p>}
        <ul className="list">
          {assignments.data?.map((a) => (
            <li key={a.itemId}>
              <Link className="grow" to={`/cohorts/${cohortId}/assignments/${a.itemId}`}>
                {a.chapterPosition}장 · {a.title}
              </Link>
              <span className="muted">{dueText(a.dueAt)}</span>
              <span className="muted">
                제출 {a.submittedCount} · 승인 {a.approvedCount}
              </span>
              <StatusBadge status={a.myStatus} />
            </li>
          ))}
        </ul>
      </div>

      {progress.data && progress.data.assignments.length > 0 && (
        <div className="card">
          <h2>진척도</h2>
          <div className="table-scroll">
            <table className="progress">
              <thead>
                <tr>
                  <th>멤버</th>
                  {progress.data.assignments.map((a) => (
                    <th key={a.itemId} title={a.title}>
                      {a.chapterPosition}장
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {progress.data.members.map((m) => (
                  <tr key={m.userId}>
                    <td>{m.nickname}</td>
                    {m.cells.map((cell) => (
                      <td key={cell.itemId}>
                        <StatusBadge status={cell.status} late={cell.late} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  )
}

export function AssignmentPage() {
  const { cohortId = '', itemId = '' } = useParams()
  const client = useQueryClient()
  const listKey = ['cohort', cohortId, 'assignment', itemId]
  const list = useQuery({
    queryKey: listKey,
    queryFn: () => api<SubmissionList>(`/api/cohorts/${cohortId}/assignments/${itemId}/submissions`),
  })
  const mineId = list.data?.mine?.id
  const mine = useQuery({
    queryKey: ['submission', mineId],
    queryFn: () => api<SubmissionDetail>(`/api/submissions/${mineId}`),
    enabled: !!mineId,
  })
  const refresh = () => {
    client.invalidateQueries({ queryKey: listKey })
    client.invalidateQueries({ queryKey: ['submission'] })
    client.invalidateQueries({ queryKey: ['cohort', cohortId] })
  }

  if (list.error) return <main className="error">{errorText(list.error)}</main>
  if (!list.data) return <main className="muted">불러오는 중…</main>
  const l = list.data

  return (
    <main>
      <p className="muted">
        <Link to={`/cohorts/${cohortId}`}>← 기수로</Link>
      </p>
      <div className="card">
        <h1 style={{ marginBottom: 4 }}>{l.title}</h1>
        <p className="muted">{dueText(l.dueAt)}</p>
        <Markdown source={l.descriptionMd} />
      </div>

      {(!mineId || mine.data) && (
        <MySubmissionEditor key={mine.data?.version ?? 'new'} cohortId={cohortId} itemId={itemId} current={mine.data ?? null} onSaved={refresh} />
      )}

      <div className="card">
        <h2>다른 멤버의 제출</h2>
        {!l.canViewOthers ? (
          <p className="muted">내 과제를 먼저 제출하면 다른 사람의 제출물을 볼 수 있습니다. 마감이 지나도 열립니다.</p>
        ) : l.others.length === 0 ? (
          <p className="muted">아직 제출한 사람이 없습니다.</p>
        ) : (
          <ul className="list">
            {l.others.map((s) => (
              <li key={s.id}>
                <Link className="grow" to={`/submissions/${s.id}`}>
                  {s.authorNickname}
                </Link>
                <span className="muted">{s.submittedAt && formatDate(s.submittedAt)}</span>
                <span className="muted">코멘트 {s.commentCount}</span>
                <StatusBadge status={s.status} late={s.late} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  )
}

function MySubmissionEditor(props: {
  cohortId: string
  itemId: string
  current: SubmissionDetail | null
  onSaved: () => void
}) {
  const { cohortId, itemId, current, onSaved } = props
  const [body, setBody] = useState(current?.bodyMd ?? '')
  const [preview, setPreview] = useState(false)
  const base = `/api/cohorts/${cohortId}/assignments/${itemId}/my-submission`
  const save = useMutation({
    mutationFn: () => api<SubmissionDetail>(base, { method: 'PUT', body: { bodyMd: body, version: current?.version ?? null } }),
    onSuccess: onSaved,
  })
  const submit = useMutation({
    mutationFn: async () => {
      if (!current || body !== current.bodyMd) {
        await api<SubmissionDetail>(base, { method: 'PUT', body: { bodyMd: body, version: current?.version ?? null } })
      }
      return api<SubmissionDetail>(`${base}/submit`, { method: 'POST' })
    },
    onSuccess: onSaved,
  })

  const status = current?.status ?? null
  const locked = status === 'APPROVED'
  const dirty = body !== (current?.bodyMd ?? '')
  const canSubmit = !locked && body.trim().length > 0 && (status !== 'SUBMITTED' || dirty)

  return (
    <div className="card">
      <div className="row">
        <h2 className="grow" style={{ margin: 0 }}>
          내 제출
        </h2>
        <StatusBadge status={status} late={current?.late} />
        {current && current.status !== 'DRAFT' && <Link to={`/submissions/${current.id}`}>리뷰 보기 ({current.comments.length})</Link>}
      </div>
      {status === 'CHANGES_REQUESTED' && <p className="notice">수정 요청을 받았습니다. 리뷰를 확인하고 고친 뒤 다시 제출하세요.</p>}
      {locked ? (
        <Markdown source={current?.bodyMd} />
      ) : (
        <>
          <div className="row" style={{ marginTop: 8 }}>
            <span className="grow muted">마크다운으로 작성합니다. 실행계획은 ``` 코드 블록에 붙여 넣으세요.</span>
            <button type="button" className="secondary" onClick={() => setPreview(!preview)}>
              {preview ? '편집' : '미리보기'}
            </button>
          </div>
          {preview ? (
            <div className="card">
              <Markdown source={body} />
            </div>
          ) : (
            <textarea rows={16} maxLength={100000} value={body} onChange={(e) => setBody(e.target.value)} />
          )}
          {(save.error || submit.error) && <p className="error">{errorText(save.error ?? submit.error)}</p>}
          <div className="row" style={{ marginTop: 12 }}>
            <button className="secondary" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
              임시 저장
            </button>
            <button disabled={!canSubmit || submit.isPending} onClick={() => submit.mutate()}>
              {status === 'CHANGES_REQUESTED' ? '다시 제출' : status === 'SUBMITTED' ? '수정본 저장' : '제출'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

export function SubmissionPage() {
  const { submissionId = '' } = useParams()
  const client = useQueryClient()
  const key = ['submission', submissionId]
  const submission = useQuery({ queryKey: key, queryFn: () => api<SubmissionDetail>(`/api/submissions/${submissionId}`) })
  const refresh = () => {
    client.invalidateQueries({ queryKey: key })
    client.invalidateQueries({ queryKey: ['cohort'] })
  }

  if (submission.error) return <main className="error">{errorText(submission.error)}</main>
  if (!submission.data) return <main className="muted">불러오는 중…</main>
  const s = submission.data
  const topLevel = s.comments.filter((c) => !c.parentId)
  const replies = (parentId: string) => s.comments.filter((c) => c.parentId === parentId)

  return (
    <main>
      <p className="muted">
        <Link to={`/cohorts/${s.cohortId}/assignments/${s.itemId}`}>← {s.title}</Link>
      </p>
      <div className="card">
        <div className="row">
          <h1 className="grow" style={{ margin: 0 }}>
            {s.authorNickname}의 제출
          </h1>
          <StatusBadge status={s.status} late={s.late} />
        </div>
        <p className="muted">
          {s.submittedAt ? `${formatDate(s.submittedAt)} 제출` : '아직 제출 전'} · {dueText(s.dueAt)}
        </p>
        <Markdown source={s.bodyMd} />
      </div>

      {s.canReview && <ReviewBox submissionId={s.id} onDone={refresh} />}

      <div className="card">
        <h2>리뷰 {s.comments.filter((c) => !c.deleted).length}</h2>
        {topLevel.map((c) => (
          <div key={c.id} className="comment-thread">
            <CommentItem comment={c} onChanged={refresh} />
            {replies(c.id).map((r) => (
              <div key={r.id} className="comment-reply">
                <CommentItem comment={r} onChanged={refresh} />
              </div>
            ))}
            {s.status !== 'DRAFT' && !c.deleted && <CommentForm submissionId={s.id} parentId={c.id} onDone={refresh} compact />}
          </div>
        ))}
        {s.status !== 'DRAFT' && <CommentForm submissionId={s.id} onDone={refresh} />}
      </div>
    </main>
  )
}

function ReviewBox({ submissionId, onDone }: { submissionId: string; onDone: () => void }) {
  const [body, setBody] = useState('')
  const review = useMutation({
    mutationFn: (decision: 'APPROVE' | 'REQUEST_CHANGES') =>
      api(`/api/submissions/${submissionId}/reviews`, { method: 'POST', body: { decision, bodyMd: body } }),
    onSuccess: () => {
      setBody('')
      onDone()
    },
  })
  return (
    <div className="card">
      <h2>리뷰하기</h2>
      <textarea rows={4} maxLength={20000} placeholder="리뷰 의견 (선택)" value={body} onChange={(e) => setBody(e.target.value)} />
      {review.error && <p className="error">{errorText(review.error)}</p>}
      <div className="row" style={{ marginTop: 8 }}>
        <button disabled={review.isPending} onClick={() => review.mutate('APPROVE')}>
          승인
        </button>
        <button className="danger" disabled={review.isPending} onClick={() => review.mutate('REQUEST_CHANGES')}>
          수정 요청
        </button>
      </div>
    </div>
  )
}

function CommentItem({ comment, onChanged }: { comment: ReviewComment; onChanged: () => void }) {
  const [editing, setEditing] = useState(false)
  const [body, setBody] = useState(comment.bodyMd)
  const edit = useMutation({
    mutationFn: () => api(`/api/review-comments/${comment.id}`, { method: 'PATCH', body: { bodyMd: body } }),
    onSuccess: () => {
      setEditing(false)
      onChanged()
    },
  })
  const remove = useMutation({
    mutationFn: () => api(`/api/review-comments/${comment.id}`, { method: 'DELETE' }),
    onSuccess: onChanged,
  })

  if (comment.deleted) return <p className="muted comment">삭제된 코멘트입니다.</p>
  return (
    <div className="comment">
      <div className="row">
        <strong>{comment.authorNickname}</strong>
        {comment.decision && (
          <span className={`badge ${comment.decision === 'APPROVE' ? 'status-APPROVED' : 'status-CHANGES_REQUESTED'}`}>
            {comment.decision === 'APPROVE' ? '승인' : '수정 요청'}
          </span>
        )}
        <span className="muted grow">
          {formatDate(comment.createdAt)}
          {comment.editedAt && ' (수정됨)'}
        </span>
        {comment.canModify && !editing && (
          <>
            {!comment.decision && (
              <button className="secondary" onClick={() => setEditing(true)}>
                수정
              </button>
            )}
            <button className="danger" onClick={() => window.confirm('코멘트를 삭제할까요?') && remove.mutate()}>
              삭제
            </button>
          </>
        )}
      </div>
      {editing ? (
        <>
          <textarea rows={3} maxLength={20000} value={body} onChange={(e) => setBody(e.target.value)} />
          <div className="row" style={{ marginTop: 4 }}>
            <button disabled={!body.trim() || edit.isPending} onClick={() => edit.mutate()}>
              저장
            </button>
            <button className="secondary" onClick={() => setEditing(false)}>
              취소
            </button>
          </div>
        </>
      ) : (
        <Markdown source={comment.bodyMd} />
      )}
      {(edit.error || remove.error) && <p className="error">{errorText(edit.error ?? remove.error)}</p>}
    </div>
  )
}

function CommentForm(props: { submissionId: string; parentId?: string; onDone: () => void; compact?: boolean }) {
  const { submissionId, parentId, onDone, compact } = props
  const [open, setOpen] = useState(!compact)
  const [body, setBody] = useState('')
  const post = useMutation({
    mutationFn: () => api(`/api/submissions/${submissionId}/comments`, { method: 'POST', body: { bodyMd: body, parentId } }),
    onSuccess: () => {
      setBody('')
      if (compact) setOpen(false)
      onDone()
    },
  })
  if (!open) {
    return (
      <button className="secondary comment-reply" onClick={() => setOpen(true)}>
        답글
      </button>
    )
  }
  return (
    <div className={compact ? 'comment-reply' : ''} style={{ marginTop: 8 }}>
      <textarea rows={compact ? 2 : 3} maxLength={20000} placeholder={parentId ? '답글' : '코멘트 (마크다운)'} value={body} onChange={(e) => setBody(e.target.value)} />
      {post.error && <p className="error">{errorText(post.error)}</p>}
      <div className="row" style={{ marginTop: 4 }}>
        <button disabled={!body.trim() || post.isPending} onClick={() => post.mutate()}>
          {parentId ? '답글 달기' : '코멘트 달기'}
        </button>
        {compact && (
          <button className="secondary" onClick={() => setOpen(false)}>
            취소
          </button>
        )}
      </div>
    </div>
  )
}
