-- M2: 코스(무엇을 공부하나)와 기수(누가 언제 공부하나)
-- 코스 내용은 course_version 단위로 묶는다. M2에서는 버전 1(DRAFT)만 편집하고, 공개·개정은 M7에서 다룬다.

create table book (
    id         uuid         primary key default gen_random_uuid(),
    title      varchar(200) not null,
    author     varchar(200),
    publisher  varchar(100),
    isbn       varchar(20),
    created_at timestamptz  not null default now()
);

create table course (
    id             uuid         primary key default gen_random_uuid(),
    book_id        uuid         not null references book (id),
    -- 코스를 만든 그룹. 초안 편집 권한의 기준이다
    owner_group_id uuid         not null references study_group (id) on delete cascade,
    title          varchar(100) not null,
    summary        varchar(1000),
    created_by     uuid         not null references app_user (id),
    created_at     timestamptz  not null default now()
);
create index course_owner_group_ix on course (owner_group_id);

create table course_version (
    id           uuid        primary key default gen_random_uuid(),
    course_id    uuid        not null references course (id) on delete cascade,
    version_no   integer     not null,
    status       varchar(16) not null check (status in ('DRAFT', 'PUBLISHED')),
    published_at timestamptz,
    created_at   timestamptz not null default now(),
    constraint course_version_uk unique (course_id, version_no)
);

create table chapter (
    id                uuid         primary key default gen_random_uuid(),
    course_version_id uuid         not null references course_version (id) on delete cascade,
    position          integer      not null,
    -- 책의 장 이름만 적는다(본문을 옮기지 않는다)
    title             varchar(200) not null,
    goals             varchar(2000),
    guide_md          text         check (length(guide_md) <= 50000),
    version           bigint       not null default 0
);
create index chapter_version_ix on chapter (course_version_id, position);

create table chapter_item (
    id         uuid         primary key default gen_random_uuid(),
    chapter_id uuid         not null references chapter (id) on delete cascade,
    position   integer      not null,
    type       varchar(20)  not null check (type in ('THEORY_GUIDE', 'LAB', 'ASSIGNMENT', 'PROBLEM')),
    title      varchar(200) not null,
    body_md    text         check (length(body_md) <= 50000),
    version    bigint       not null default 0
);
create index chapter_item_chapter_ix on chapter_item (chapter_id, position);

create table cohort (
    id                uuid         primary key default gen_random_uuid(),
    group_id          uuid         not null references study_group (id) on delete cascade,
    course_version_id uuid         not null references course_version (id),
    name              varchar(100) not null,
    starts_on         date         not null,
    status            varchar(16)  not null check (status in ('PLANNED', 'RUNNING', 'COMPLETED')),
    created_by        uuid         not null references app_user (id),
    created_at        timestamptz  not null default now()
);
create index cohort_group_ix on cohort (group_id);

-- 기수의 장별 일정. 장이 나중에 추가되면 일정 없이 표시되고, 일정을 정할 때 행이 생긴다
create table cohort_session (
    id             uuid         primary key default gen_random_uuid(),
    cohort_id      uuid         not null references cohort (id) on delete cascade,
    chapter_id     uuid         not null references chapter (id) on delete cascade,
    scheduled_at   timestamptz,
    presenter_id   uuid         references app_user (id) on delete set null,
    note           varchar(500),
    constraint cohort_session_uk unique (cohort_id, chapter_id)
);
