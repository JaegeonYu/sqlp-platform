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

export type ItemType = 'THEORY_GUIDE' | 'LAB' | 'ASSIGNMENT' | 'PROBLEM'
export type CohortStatus = 'PLANNED' | 'RUNNING' | 'COMPLETED'

export type Book = { title: string; author: string | null; publisher: string | null; isbn: string | null }

export type CourseSummary = { id: string; title: string; bookTitle: string; chapterCount: number; createdAt: string }

export type ChapterItem = { id: string; position: number; type: ItemType; title: string; bodyMd: string | null; version: number }

export type Chapter = {
  id: string
  position: number
  title: string
  goals: string | null
  guideMd: string | null
  version: number
  items: ChapterItem[]
}

export type CourseDetail = {
  id: string
  title: string
  summary: string | null
  ownerGroupId: string
  book: Book
  versionId: string
  versionNo: number
  status: 'DRAFT' | 'PUBLISHED'
  canEdit: boolean
  chapters: Chapter[]
}

export type CourseRef = { id: string; title: string; bookTitle: string; versionNo: number }

export type CohortSummary = { id: string; name: string; startsOn: string; status: CohortStatus; course: CourseRef }

export type SessionRow = {
  chapterId: string
  position: number
  chapterTitle: string
  scheduledAt: string | null
  presenterId: string | null
  presenterNickname: string | null
  note: string | null
}

export type CohortDetail = {
  id: string
  groupId: string
  name: string
  startsOn: string
  status: CohortStatus
  course: CourseRef
  canManage: boolean
  schedule: SessionRow[]
  members: Member[]
}

export type SubmissionStatus = 'DRAFT' | 'SUBMITTED' | 'CHANGES_REQUESTED' | 'APPROVED'

export type AssignmentRow = {
  itemId: string
  title: string
  descriptionMd: string | null
  chapterPosition: number
  chapterTitle: string
  dueAt: string | null
  myStatus: SubmissionStatus | null
  submittedCount: number
  approvedCount: number
}

export type Progress = {
  assignments: { itemId: string; title: string; chapterPosition: number; dueAt: string | null }[]
  members: {
    userId: string
    nickname: string
    cells: { itemId: string; submissionId: string | null; status: SubmissionStatus | null; late: boolean }[]
  }[]
}

export type SubmissionSummary = {
  id: string
  authorId: string
  authorNickname: string
  status: SubmissionStatus
  submittedAt: string | null
  late: boolean
  commentCount: number
}

export type SubmissionList = {
  itemId: string
  title: string
  descriptionMd: string | null
  dueAt: string | null
  canViewOthers: boolean
  mine: SubmissionSummary | null
  others: SubmissionSummary[]
}

export type ReviewComment = {
  id: string
  authorId: string
  authorNickname: string
  parentId: string | null
  decision: 'APPROVE' | 'REQUEST_CHANGES' | null
  bodyMd: string
  deleted: boolean
  createdAt: string
  editedAt: string | null
  canModify: boolean
}

export type SubmissionDetail = {
  id: string
  cohortId: string
  itemId: string
  title: string
  descriptionMd: string | null
  dueAt: string | null
  authorId: string
  authorNickname: string
  bodyMd: string
  status: SubmissionStatus
  submittedAt: string | null
  late: boolean
  updatedAt: string
  version: number
  canEdit: boolean
  canReview: boolean
  comments: ReviewComment[]
}

export const submissionStatusLabel: Record<SubmissionStatus, string> = {
  DRAFT: '작성 중',
  SUBMITTED: '리뷰 대기',
  CHANGES_REQUESTED: '수정 요청',
  APPROVED: '승인',
}

export const itemTypeLabel: Record<ItemType, string> = {
  THEORY_GUIDE: '이론 가이드',
  LAB: '실습',
  ASSIGNMENT: '과제',
  PROBLEM: '튜닝 문제',
}

export const cohortStatusLabel: Record<CohortStatus, string> = {
  PLANNED: '준비 중',
  RUNNING: '진행 중',
  COMPLETED: '완료',
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
