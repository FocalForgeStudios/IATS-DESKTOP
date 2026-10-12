-- IATS Connect desktop application schema and security policies.
-- Review against existing production policies before applying; this script is additive.
create extension if not exists pgcrypto;

create table if not exists public.student_profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 name text not null, course text not null, cohort text, study_schedule text,
 avatar_url text, emoji text, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.groups (
 id uuid primary key default gen_random_uuid(), name text not null check(length(trim(name)) between 2 and 80),
 description text, course text, cohort text, parent_group_id uuid references public.groups(id) on delete cascade,
 created_by uuid not null references auth.users(id) on delete cascade,
 visibility text not null default 'public' check(visibility in ('public','private')),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.group_memberships (
 group_id uuid not null references public.groups(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 role text not null default 'member' check(role in ('owner','moderator','member')),
 joined_at timestamptz not null default now(), primary key(group_id,user_id)
);
create table if not exists public.direct_messages (
 id uuid primary key default gen_random_uuid(), sender_id uuid not null references auth.users(id) on delete cascade,
 recipient_id uuid not null references auth.users(id) on delete cascade,
 body text not null check(length(trim(body)) between 1 and 4000), created_at timestamptz not null default now(),
 read_at timestamptz, constraint direct_messages_no_self check(sender_id <> recipient_id)
);
create table if not exists public.group_messages (
 id uuid primary key default gen_random_uuid(), group_id uuid not null references public.groups(id) on delete cascade,
 sender_id uuid not null references auth.users(id) on delete cascade,
 body text not null check(length(trim(body)) between 1 and 4000), created_at timestamptz not null default now()
);
create table if not exists public.study_buddy_requests (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 course text not null, cohort text, study_schedule text, message text,
 status text not null default 'open' check(status in ('open','matched','closed')),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), unique(user_id)
);
create table if not exists public.learning_materials (
 id uuid primary key default gen_random_uuid(), title text not null, description text,
 course text, cohort text, storage_path text not null, file_name text not null,
 uploaded_by uuid not null references auth.users(id) on delete cascade,
 visibility text not null default 'students' check(visibility in ('students','public')),
 created_at timestamptz not null default now()
);

create index if not exists groups_course_cohort_idx on public.groups(course,cohort);
create index if not exists memberships_user_idx on public.group_memberships(user_id);
create index if not exists group_messages_group_created_idx on public.group_messages(group_id,created_at desc);
create index if not exists direct_messages_pair_created_idx on public.direct_messages(sender_id,recipient_id,created_at desc);
create index if not exists buddy_status_course_idx on public.study_buddy_requests(status,course,cohort);

alter table public.student_profiles enable row level security;
alter table public.groups enable row level security;
alter table public.group_memberships enable row level security;
alter table public.direct_messages enable row level security;
alter table public.group_messages enable row level security;
alter table public.study_buddy_requests enable row level security;
alter table public.learning_materials enable row level security;

grant select on public.student_profiles to authenticated;
grant insert,update on public.student_profiles to authenticated;
grant select on public.groups to anon, authenticated;
grant insert,update,delete on public.groups to authenticated;
grant select on public.group_memberships to authenticated;
grant insert,delete on public.group_memberships to authenticated;
grant select,insert,update on public.direct_messages to authenticated;
grant select,insert on public.group_messages to authenticated;
grant select,insert,update,delete on public.study_buddy_requests to authenticated;
grant select,insert,update,delete on public.learning_materials to authenticated;

drop policy if exists student_profiles_public_read on public.student_profiles;
drop policy if exists student_profiles_self_read on public.student_profiles;
drop policy if exists student_profiles_self_insert on public.student_profiles;
drop policy if exists student_profiles_self_update on public.student_profiles;
create policy student_profiles_authenticated_read on public.student_profiles for select to authenticated using (true);
create policy student_profiles_self_insert on public.student_profiles for insert to authenticated with check (id = auth.uid());
create policy student_profiles_self_update on public.student_profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists groups_read_public_or_member on public.groups;
create policy groups_read_public_or_member on public.groups for select to anon,authenticated using (visibility='public' or exists(select 1 from public.group_memberships gm where gm.group_id=id and gm.user_id=auth.uid()));
drop policy if exists groups_insert_owner on public.groups;
create policy groups_insert_owner on public.groups for insert to authenticated with check (created_by=auth.uid());
drop policy if exists groups_update_owner on public.groups;
create policy groups_update_owner on public.groups for update to authenticated using (created_by=auth.uid()) with check (created_by=auth.uid());
drop policy if exists groups_delete_owner on public.groups;
create policy groups_delete_owner on public.groups for delete to authenticated using (created_by=auth.uid());

drop policy if exists memberships_read_authenticated on public.group_memberships;
create policy memberships_read_authenticated on public.group_memberships for select to authenticated using (true);
drop policy if exists memberships_join_self on public.group_memberships;
create policy memberships_join_self on public.group_memberships for insert to authenticated with check (user_id=auth.uid() and role='member');
drop policy if exists memberships_leave_self on public.group_memberships;
create policy memberships_leave_self on public.group_memberships for delete to authenticated using (user_id=auth.uid() or exists(select 1 from public.groups g where g.id=group_id and g.created_by=auth.uid()));

drop policy if exists direct_messages_participant_read on public.direct_messages;
create policy direct_messages_participant_read on public.direct_messages for select to authenticated using (sender_id=auth.uid() or recipient_id=auth.uid());
drop policy if exists direct_messages_sender_insert on public.direct_messages;
create policy direct_messages_sender_insert on public.direct_messages for insert to authenticated with check (sender_id=auth.uid());
drop policy if exists direct_messages_recipient_update on public.direct_messages;
create policy direct_messages_recipient_update on public.direct_messages for update to authenticated using (recipient_id=auth.uid()) with check (recipient_id=auth.uid());

drop policy if exists group_messages_member_read on public.group_messages;
create policy group_messages_member_read on public.group_messages for select to authenticated using (exists(select 1 from public.group_memberships gm where gm.group_id=public.group_messages.group_id and gm.user_id=auth.uid()));
drop policy if exists group_messages_member_insert on public.group_messages;
create policy group_messages_member_insert on public.group_messages for insert to authenticated with check (sender_id=auth.uid() and exists(select 1 from public.group_memberships gm where gm.group_id=public.group_messages.group_id and gm.user_id=auth.uid()));

drop policy if exists buddy_requests_read on public.study_buddy_requests;
create policy buddy_requests_read on public.study_buddy_requests for select to authenticated using (status='open' or user_id=auth.uid());
drop policy if exists buddy_requests_insert_self on public.study_buddy_requests;
create policy buddy_requests_insert_self on public.study_buddy_requests for insert to authenticated with check (user_id=auth.uid());
drop policy if exists buddy_requests_update_self on public.study_buddy_requests;
create policy buddy_requests_update_self on public.study_buddy_requests for update to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());
drop policy if exists buddy_requests_delete_self on public.study_buddy_requests;
create policy buddy_requests_delete_self on public.study_buddy_requests for delete to authenticated using (user_id=auth.uid());

drop policy if exists materials_read_allowed on public.learning_materials;
create policy materials_read_allowed on public.learning_materials for select to authenticated using (visibility in ('students','public'));
drop policy if exists materials_insert_uploader on public.learning_materials;
create policy materials_insert_uploader on public.learning_materials for insert to authenticated with check (uploaded_by=auth.uid());
drop policy if exists materials_update_uploader on public.learning_materials;
create policy materials_update_uploader on public.learning_materials for update to authenticated using (uploaded_by=auth.uid()) with check (uploaded_by=auth.uid());
drop policy if exists materials_delete_uploader on public.learning_materials;
create policy materials_delete_uploader on public.learning_materials for delete to authenticated using (uploaded_by=auth.uid());

-- Existing official content policies must continue to restrict writes to the approved admin identity.
-- Do not grant anonymous insert/update/delete on hub_posts or catchups.

-- Automatically make a group creator its owner-member, so private groups are usable immediately.
create or replace function public.iats_add_group_creator_membership() returns trigger
language plpgsql security definer set search_path = public as $$
begin
 insert into public.group_memberships(group_id,user_id,role) values(new.id,new.created_by,'owner') on conflict(group_id,user_id) do nothing;
 return new;
end; $$;
drop trigger if exists groups_add_creator_membership on public.groups;
create trigger groups_add_creator_membership after insert on public.groups for each row execute function public.iats_add_group_creator_membership();
revoke all on function public.iats_add_group_creator_membership() from public, anon, authenticated;
