-- M3: 기수 과제 제출과 리뷰(GitHub PR 리뷰 흐름 대체)
-- 과제는 코스의 chapter_item(type=ASSIGNMENT)이고, 마감은 그 장의 cohort_session.scheduled_at이다

create table submission (
    id            uuid        primary key default gen_random_uuid(),
    cohort_id     uuid        not null references cohort (id) on delete cascade,
    item_id       uuid        not null references chapter_item (id) on delete cascade,
    author_id     uuid        not null references app_user (id) on delete cascade,
    body_md       text        not null default '' check (length(body_md) <= 100000),
    status        varchar(20) not null check (status in ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED')),
    submitted_at  timestamptz,
    reviewed_by   uuid        references app_user (id) on delete set null,
    reviewed_at   timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    version       bigint      not null default 0,
    constraint submission_uk unique (cohort_id, item_id, author_id)
);
create index submission_cohort_item_ix on submission (cohort_id, item_id);

create table review_comment (
    id            uuid        primary key default gen_random_uuid(),
    submission_id uuid        not null references submission (id) on delete cascade,
    author_id     uuid        not null references app_user (id) on delete cascade,
    -- 답글은 한 단계만 허용한다
    parent_id     uuid        references review_comment (id) on delete cascade,
    -- 리뷰 결정과 함께 남긴 코멘트(APPROVE / REQUEST_CHANGES), 일반 코멘트는 null
    decision      varchar(20) check (decision in ('APPROVE', 'REQUEST_CHANGES')),
    body_md       text        not null check (length(body_md) <= 20000),
    deleted       boolean     not null default false,
    created_at    timestamptz not null default now(),
    edited_at     timestamptz
);
create index review_comment_submission_ix on review_comment (submission_id, created_at);
