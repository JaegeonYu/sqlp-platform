import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, itemTypeLabel, type Chapter, type ChapterItem, type CourseDetail, type ItemType } from '../api'
import { Markdown } from '../Markdown'

const editableTypes: ItemType[] = ['THEORY_GUIDE', 'LAB', 'ASSIGNMENT']

function errorText(error: unknown): string | null {
  return error instanceof Error ? error.message : null
}

function move<T>(list: T[], index: number, delta: number): T[] {
  const next = [...list]
  const [picked] = next.splice(index, 1)
  next.splice(index + delta, 0, picked)
  return next
}

export function CoursePage() {
  const { courseId = '' } = useParams()
  const client = useQueryClient()
  const course = useQuery({ queryKey: ['course', courseId], queryFn: () => api<CourseDetail>(`/api/courses/${courseId}`) })
  const refresh = () => client.invalidateQueries({ queryKey: ['course', courseId] })
  const [editingInfo, setEditingInfo] = useState(false)

  const reorder = useMutation({
    mutationFn: (ids: string[]) => api(`/api/courses/${courseId}/chapter-order`, { method: 'PUT', body: { ids } }),
    onSettled: refresh,
  })

  if (course.error) return <main className="error">{errorText(course.error)}</main>
  if (!course.data) return <main className="muted">불러오는 중…</main>
  const c = course.data

  return (
    <main>
      <p className="muted">
        <Link to={`/groups/${c.ownerGroupId}`}>← 그룹으로</Link>
      </p>
      {editingInfo ? (
        <CourseInfoForm course={c} onDone={() => setEditingInfo(false)} />
      ) : (
        <div className="card">
          <div className="row">
            <h1 className="grow" style={{ margin: 0 }}>
              {c.title}
            </h1>
            <span className="badge">
              v{c.versionNo} {c.status === 'DRAFT' ? '초안' : '공개'}
            </span>
            {c.canEdit && (
              <button className="secondary" onClick={() => setEditingInfo(true)}>
                정보 수정
              </button>
            )}
          </div>
          <p className="muted">
            『{c.book.title}』{c.book.author && ` · ${c.book.author}`}
            {c.book.publisher && ` · ${c.book.publisher}`}
          </p>
          {c.summary && <p>{c.summary}</p>}
        </div>
      )}

      {c.canEdit && (
        <p className="muted">
          책 본문과 예제 코드를 그대로 옮기지 마세요. 장 이름, 직접 쓴 해설, 새로 만든 실습만 담습니다. 코스는 나중에 다른 그룹에 공개될 수 있습니다.
        </p>
      )}

      {c.chapters.map((chapter, index) => (
        <ChapterCard
          key={chapter.id}
          chapter={chapter}
          canEdit={c.canEdit}
          onMove={
            c.canEdit
              ? (delta) => reorder.mutate(move(c.chapters, index, delta).map((ch) => ch.id))
              : undefined
          }
          isFirst={index === 0}
          isLast={index === c.chapters.length - 1}
          refresh={refresh}
        />
      ))}
      {c.chapters.length === 0 && <p className="muted">아직 장이 없습니다.</p>}
      {c.canEdit && <NewChapterForm courseId={courseId} onAdded={refresh} />}
    </main>
  )
}

function CourseInfoForm({ course, onDone }: { course: CourseDetail; onDone: () => void }) {
  const client = useQueryClient()
  const [form, setForm] = useState({
    title: course.title,
    summary: course.summary ?? '',
    bookTitle: course.book.title,
    author: course.book.author ?? '',
    publisher: course.book.publisher ?? '',
    isbn: course.book.isbn ?? '',
  })
  const save = useMutation({
    mutationFn: () =>
      api(`/api/courses/${course.id}`, {
        method: 'PATCH',
        body: {
          title: form.title,
          summary: form.summary,
          book: { title: form.bookTitle, author: form.author, publisher: form.publisher, isbn: form.isbn },
        },
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['course', course.id] })
      onDone()
    },
  })
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })

  return (
    <form
      className="card"
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
    >
      <h2>코스 정보</h2>
      <label>코스 이름</label>
      <input required maxLength={100} value={form.title} onChange={set('title')} />
      <label>소개</label>
      <textarea rows={3} maxLength={1000} value={form.summary} onChange={set('summary')} />
      <label>책 제목</label>
      <input required maxLength={200} value={form.bookTitle} onChange={set('bookTitle')} />
      <div className="row">
        <div className="grow">
          <label>저자</label>
          <input maxLength={200} value={form.author} onChange={set('author')} />
        </div>
        <div className="grow">
          <label>출판사</label>
          <input maxLength={100} value={form.publisher} onChange={set('publisher')} />
        </div>
        <div className="grow">
          <label>ISBN</label>
          <input maxLength={20} value={form.isbn} onChange={set('isbn')} />
        </div>
      </div>
      {save.error && <p className="error">{errorText(save.error)}</p>}
      <div className="row" style={{ marginTop: 12 }}>
        <button type="submit" disabled={save.isPending}>
          저장
        </button>
        <button type="button" className="secondary" onClick={onDone}>
          취소
        </button>
      </div>
    </form>
  )
}

function ChapterCard(props: {
  chapter: Chapter
  canEdit: boolean
  onMove?: (delta: number) => void
  isFirst: boolean
  isLast: boolean
  refresh: () => void
}) {
  const { chapter, canEdit, onMove, isFirst, isLast, refresh } = props
  const [editing, setEditing] = useState(false)
  const remove = useMutation({
    mutationFn: () => api(`/api/chapters/${chapter.id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
  const reorderItems = useMutation({
    mutationFn: (ids: string[]) => api(`/api/chapters/${chapter.id}/item-order`, { method: 'PUT', body: { ids } }),
    onSettled: refresh,
  })

  return (
    <section className="card">
      {editing ? (
        <ChapterForm chapter={chapter} onDone={() => setEditing(false)} refresh={refresh} />
      ) : (
        <>
          <div className="row">
            <h2 className="grow" style={{ margin: 0 }}>
              {chapter.position}. {chapter.title}
            </h2>
            {canEdit && (
              <>
                <button className="secondary" disabled={isFirst} onClick={() => onMove?.(-1)} aria-label="위로">
                  ↑
                </button>
                <button className="secondary" disabled={isLast} onClick={() => onMove?.(1)} aria-label="아래로">
                  ↓
                </button>
                <button className="secondary" onClick={() => setEditing(true)}>
                  편집
                </button>
                <button
                  className="danger"
                  onClick={() => window.confirm(`"${chapter.title}" 장과 그 안의 항목을 삭제할까요?`) && remove.mutate()}
                >
                  삭제
                </button>
              </>
            )}
          </div>
          {chapter.goals && <p className="muted">학습 목표: {chapter.goals}</p>}
          <Markdown source={chapter.guideMd} />
        </>
      )}

      <ul className="list" style={{ marginTop: 12 }}>
        {chapter.items.map((item, index) => (
          <ItemRow
            key={item.id}
            item={item}
            canEdit={canEdit}
            refresh={refresh}
            onMove={(delta) => reorderItems.mutate(move(chapter.items, index, delta).map((i) => i.id))}
            isFirst={index === 0}
            isLast={index === chapter.items.length - 1}
          />
        ))}
      </ul>
      {canEdit && <NewItemForm chapterId={chapter.id} onAdded={refresh} />}
    </section>
  )
}

function ChapterForm({ chapter, onDone, refresh }: { chapter: Chapter; onDone: () => void; refresh: () => void }) {
  const [title, setTitle] = useState(chapter.title)
  const [goals, setGoals] = useState(chapter.goals ?? '')
  const [guide, setGuide] = useState(chapter.guideMd ?? '')
  const [preview, setPreview] = useState(false)
  const save = useMutation({
    mutationFn: () =>
      api(`/api/chapters/${chapter.id}`, {
        method: 'PATCH',
        body: { version: chapter.version, title, goals, guideMd: guide },
      }),
    onSuccess: () => {
      refresh()
      onDone()
    },
  })

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
    >
      <label>장 이름 (책의 장 제목)</label>
      <input required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} />
      <label>학습 목표</label>
      <textarea rows={2} maxLength={2000} value={goals} onChange={(e) => setGoals(e.target.value)} />
      <div className="row" style={{ marginTop: 12 }}>
        <span className="grow">이론 가이드 (마크다운)</span>
        <button type="button" className="secondary" onClick={() => setPreview(!preview)}>
          {preview ? '편집' : '미리보기'}
        </button>
      </div>
      {preview ? (
        <div className="card">
          <Markdown source={guide} />
        </div>
      ) : (
        <textarea rows={12} maxLength={50000} value={guide} onChange={(e) => setGuide(e.target.value)} />
      )}
      {save.error && <p className="error">{errorText(save.error)}</p>}
      <div className="row" style={{ marginTop: 12 }}>
        <button type="submit" disabled={save.isPending}>
          저장
        </button>
        <button type="button" className="secondary" onClick={onDone}>
          취소
        </button>
      </div>
    </form>
  )
}

function ItemRow(props: {
  item: ChapterItem
  canEdit: boolean
  refresh: () => void
  onMove: (delta: number) => void
  isFirst: boolean
  isLast: boolean
}) {
  const { item, canEdit, refresh, onMove, isFirst, isLast } = props
  const [editing, setEditing] = useState(false)
  const remove = useMutation({
    mutationFn: () => api(`/api/chapter-items/${item.id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })

  if (editing) {
    return (
      <li style={{ display: 'block' }}>
        <ItemForm item={item} onDone={() => setEditing(false)} refresh={refresh} />
      </li>
    )
  }
  return (
    <li style={{ display: 'block' }}>
      <details>
        <summary className="row">
          <span className="badge">{itemTypeLabel[item.type]}</span>
          <span className="grow">{item.title}</span>
          {canEdit && (
            <>
              <button className="secondary" disabled={isFirst} onClick={(e) => (e.preventDefault(), onMove(-1))} aria-label="위로">
                ↑
              </button>
              <button className="secondary" disabled={isLast} onClick={(e) => (e.preventDefault(), onMove(1))} aria-label="아래로">
                ↓
              </button>
              <button className="secondary" onClick={(e) => (e.preventDefault(), setEditing(true))}>
                편집
              </button>
              <button
                className="danger"
                onClick={(e) => {
                  e.preventDefault()
                  if (window.confirm(`"${item.title}" 항목을 삭제할까요?`)) remove.mutate()
                }}
              >
                삭제
              </button>
            </>
          )}
        </summary>
        <Markdown source={item.bodyMd} />
      </details>
    </li>
  )
}

function ItemForm({ item, onDone, refresh }: { item: ChapterItem; onDone: () => void; refresh: () => void }) {
  const [type, setType] = useState<ItemType>(item.type)
  const [title, setTitle] = useState(item.title)
  const [body, setBody] = useState(item.bodyMd ?? '')
  const save = useMutation({
    mutationFn: () =>
      api(`/api/chapter-items/${item.id}`, { method: 'PATCH', body: { version: item.version, type, title, bodyMd: body } }),
    onSuccess: () => {
      refresh()
      onDone()
    },
  })
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
    >
      <div className="row">
        <select style={{ width: 'auto' }} value={type} onChange={(e) => setType(e.target.value as ItemType)}>
          {editableTypes.map((t) => (
            <option key={t} value={t}>
              {itemTypeLabel[t]}
            </option>
          ))}
        </select>
        <input className="grow" style={{ width: 'auto' }} required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <label>내용 (마크다운)</label>
      <textarea rows={8} maxLength={50000} value={body} onChange={(e) => setBody(e.target.value)} />
      {save.error && <p className="error">{errorText(save.error)}</p>}
      <div className="row" style={{ marginTop: 8 }}>
        <button type="submit" disabled={save.isPending}>
          저장
        </button>
        <button type="button" className="secondary" onClick={onDone}>
          취소
        </button>
      </div>
    </form>
  )
}

function NewItemForm({ chapterId, onAdded }: { chapterId: string; onAdded: () => void }) {
  const [type, setType] = useState<ItemType>('LAB')
  const [title, setTitle] = useState('')
  const add = useMutation({
    mutationFn: () => api(`/api/chapters/${chapterId}/items`, { method: 'POST', body: { type, title } }),
    onSuccess: () => {
      setTitle('')
      onAdded()
    },
  })
  function submit(e: FormEvent) {
    e.preventDefault()
    add.mutate()
  }
  return (
    <form className="row" style={{ marginTop: 8 }} onSubmit={submit}>
      <select style={{ width: 'auto' }} value={type} onChange={(e) => setType(e.target.value as ItemType)}>
        {editableTypes.map((t) => (
          <option key={t} value={t}>
            {itemTypeLabel[t]}
          </option>
        ))}
      </select>
      <input className="grow" style={{ width: 'auto' }} required maxLength={200} placeholder="항목 제목" value={title} onChange={(e) => setTitle(e.target.value)} />
      <button type="submit" className="secondary" disabled={add.isPending}>
        항목 추가
      </button>
      {add.error && <p className="error">{errorText(add.error)}</p>}
    </form>
  )
}

function NewChapterForm({ courseId, onAdded }: { courseId: string; onAdded: () => void }) {
  const [title, setTitle] = useState('')
  const add = useMutation({
    mutationFn: () => api(`/api/courses/${courseId}/chapters`, { method: 'POST', body: { title } }),
    onSuccess: () => {
      setTitle('')
      onAdded()
    },
  })
  return (
    <form
      className="card row"
      onSubmit={(e) => {
        e.preventDefault()
        add.mutate()
      }}
    >
      <input className="grow" style={{ width: 'auto' }} required maxLength={200} placeholder="새 장 이름 (예: 3장 인덱스 튜닝)" value={title} onChange={(e) => setTitle(e.target.value)} />
      <button type="submit" disabled={add.isPending}>
        장 추가
      </button>
      {add.error && <p className="error">{errorText(add.error)}</p>}
    </form>
  )
}
