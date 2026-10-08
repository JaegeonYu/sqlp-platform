-- M1: 사용자·가입 승인, 스터디 그룹·멤버·초대, 감사 로그, 세션 저장소

create table app_user (
    id                 uuid         primary key default gen_random_uuid(),
    email              varchar(254) not null,
    nickname           varchar(40)  not null,
    -- Google 전용 계정은 비밀번호가 없다
    password_hash      varchar(255),
    status             varchar(16)  not null check (status in ('PENDING', 'ACTIVE', 'REJECTED', 'SUSPENDED')),
    system_role        varchar(16)  not null default 'USER' check (system_role in ('USER', 'ADMIN')),
    signup_note        varchar(500),
    failed_login_count integer      not null default 0,
    locked_until       timestamptz,
    reviewed_by        uuid         references app_user (id),
    reviewed_at        timestamptz,
    created_at         timestamptz  not null default now(),
    version            bigint       not null default 0
);
create unique index app_user_email_uk on app_user (lower(email));
create index app_user_status_ix on app_user (status, created_at);

create table user_identity (
    id         uuid         primary key default gen_random_uuid(),
    user_id    uuid         not null references app_user (id) on delete cascade,
    provider   varchar(16)  not null check (provider in ('GOOGLE')),
    subject    varchar(255) not null,
    created_at timestamptz  not null default now(),
    constraint user_identity_uk unique (provider, subject)
);

create table study_group (
    id          uuid         primary key default gen_random_uuid(),
    name        varchar(60)  not null,
    description varchar(500),
    created_by  uuid         not null references app_user (id),
    created_at  timestamptz  not null default now()
);

create table membership (
    group_id  uuid        not null references study_group (id) on delete cascade,
    user_id   uuid        not null references app_user (id) on delete cascade,
    role      varchar(16) not null check (role in ('OWNER', 'MANAGER', 'MEMBER')),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);
create index membership_user_ix on membership (user_id);

-- 초대 토큰은 원문을 저장하지 않고 SHA-256 해시만 저장한다
create table group_invite (
    id         uuid        primary key default gen_random_uuid(),
    group_id   uuid        not null references study_group (id) on delete cascade,
    token_hash varchar(64) not null unique,
    created_by uuid        not null references app_user (id),
    expires_at timestamptz not null,
    max_uses   integer     not null check (max_uses between 1 and 100),
    use_count  integer     not null default 0,
    revoked    boolean     not null default false,
    created_at timestamptz not null default now()
);
create index group_invite_group_ix on group_invite (group_id);

create table audit_log (
    id          bigint       generated always as identity primary key,
    occurred_at timestamptz  not null default now(),
    actor_id    uuid,
    action      varchar(40)  not null,
    target      varchar(255),
    ip          varchar(45),
    detail      varchar(500)
);
create index audit_log_time_ix on audit_log (occurred_at);

-- Spring Session JDBC (spring-session-jdbc 4.1 schema-postgresql.sql)
create table spring_session (
    primary_id            char(36)     not null,
    session_id            char(36)     not null,
    creation_time         bigint       not null,
    last_access_time      bigint       not null,
    max_inactive_interval integer      not null,
    expiry_time           bigint       not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);
create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id) references spring_session (primary_id) on delete cascade
);
