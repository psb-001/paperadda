-- ============================================================
-- PaperAdda backend setup — Supabase (Postgres)
-- ============================================================

begin;

create table if not exists subjects (
  id text primary key,
  name text not null,
  branch_code text not null,
  academic_year int not null default 1,
  paper_count int not null default 0,
  icon_name text not null default 'graphic_eq'
);

create table if not exists papers (
  id text primary key,
  title text not null,
  subject_name text not null,
  branch_code text not null,
  year text not null,
  exam_type text not null default 'End Semester Examination',
  file_format text not null default 'PDF',
  file_size text not null default '',
  duration text not null default '3 Hours',
  max_marks int not null default 100,
  sample_questions text[] not null default '{}',
  storage_path text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists notes (
  id bigint generated always as identity primary key,
  title text not null,
  content text not null default '',
  subject_name text not null default '',
  branch_code text not null default '',
  academic_year int not null default 1,
  file_url text not null default '',
  storage_path text not null default '',
  updated_at timestamptz not null default now()
);

create table if not exists admin_users (
  email text primary key,
  created_at timestamptz not null default now()
);

create table if not exists content_requests (
  id uuid primary key default gen_random_uuid(),
  requester_key uuid not null,
  kind text not null check (kind in ('paper', 'note')),
  branch_code text not null check (branch_code in ('COMMON', 'ENTC', 'AIML', 'CE', 'IT')),
  academic_year int not null check (academic_year between 1 and 4),
  title text not null check (char_length(title) between 2 and 120),
  details text not null default '' check (char_length(details) <= 500),
  status text not null default 'pending' check (status in ('pending', 'fulfilled', 'rejected')),
  admin_note text not null default '' check (char_length(admin_note) <= 300),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_content_requests_status_created
  on public.content_requests (status, created_at desc);
create index if not exists idx_content_requests_requester_created
  on public.content_requests (requester_key, created_at desc);

create table if not exists feedback (
  id uuid primary key default gen_random_uuid(),
  feedback_key uuid not null,
  category text not null check (category in ('bug', 'content', 'feature', 'other')),
  message text not null check (char_length(message) between 10 and 2000),
  email text not null default '' check (char_length(email) <= 254),
  app_version text not null default '' check (char_length(app_version) <= 40),
  platform text not null default 'android' check (char_length(platform) <= 20),
  status text not null default 'new' check (status in ('new', 'reviewed', 'resolved', 'dismissed')),
  admin_note text not null default '' check (char_length(admin_note) <= 500),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_feedback_status_created
  on public.feedback (status, created_at desc);
create index if not exists idx_feedback_key_created
  on public.feedback (feedback_key, created_at desc);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_papers_updated on public.papers;
create trigger trg_papers_updated
  before update on public.papers
  for each row execute function public.touch_updated_at();

drop trigger if exists trg_notes_updated on public.notes;
create trigger trg_notes_updated
  before update on public.notes
  for each row execute function public.touch_updated_at();

drop trigger if exists trg_content_requests_updated on public.content_requests;
create trigger trg_content_requests_updated
  before update on public.content_requests
  for each row execute function public.touch_updated_at();

drop trigger if exists trg_feedback_updated on public.feedback;
create trigger trg_feedback_updated
  before update on public.feedback
  for each row execute function public.touch_updated_at();

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated, service_role;

create or replace function private.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.admin_users
    where lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
  );
$$;

revoke all on function private.is_admin() from public;
revoke all on function private.is_admin() from anon;
grant execute on function private.is_admin() to authenticated, service_role;

alter table public.subjects enable row level security;
alter table public.papers enable row level security;
alter table public.notes enable row level security;
alter table public.admin_users enable row level security;
alter table public.content_requests enable row level security;
alter table public.feedback enable row level security;

drop policy if exists "public read" on public.subjects;
drop policy if exists "admin write" on public.subjects;
drop policy if exists "admin insert" on public.subjects;
drop policy if exists "admin update" on public.subjects;
drop policy if exists "admin delete" on public.subjects;
create policy "public read" on public.subjects
  for select to anon, authenticated using (true);
create policy "admin insert" on public.subjects
  for insert to authenticated with check (private.is_admin());
create policy "admin update" on public.subjects
  for update to authenticated using (private.is_admin()) with check (private.is_admin());
create policy "admin delete" on public.subjects
  for delete to authenticated using (private.is_admin());

drop policy if exists "public read" on public.papers;
drop policy if exists "admin write" on public.papers;
drop policy if exists "admin insert" on public.papers;
drop policy if exists "admin update" on public.papers;
drop policy if exists "admin delete" on public.papers;
create policy "public read" on public.papers
  for select to anon, authenticated using (true);
create policy "admin insert" on public.papers
  for insert to authenticated with check (private.is_admin());
create policy "admin update" on public.papers
  for update to authenticated using (private.is_admin()) with check (private.is_admin());
create policy "admin delete" on public.papers
  for delete to authenticated using (private.is_admin());

drop policy if exists "public read" on public.notes;
drop policy if exists "admin write" on public.notes;
drop policy if exists "admin insert" on public.notes;
drop policy if exists "admin update" on public.notes;
drop policy if exists "admin delete" on public.notes;
create policy "public read" on public.notes
  for select to anon, authenticated using (true);
create policy "admin insert" on public.notes
  for insert to authenticated with check (private.is_admin());
create policy "admin update" on public.notes
  for update to authenticated using (private.is_admin()) with check (private.is_admin());
create policy "admin delete" on public.notes
  for delete to authenticated using (private.is_admin());

drop policy if exists "admin manage roster" on public.admin_users;
create policy "admin manage roster" on public.admin_users
  for all to authenticated using (private.is_admin()) with check (private.is_admin());

drop policy if exists "service only requests" on public.content_requests;
create policy "service only requests" on public.content_requests
  for all to anon, authenticated using (false) with check (false);

drop policy if exists "service only feedback" on public.feedback;
create policy "service only feedback" on public.feedback
  for all to anon, authenticated using (false) with check (false);

drop function if exists public.is_admin();

insert into public.admin_users (email)
values ('chillmaaryaaar@proton.me')
on conflict (email) do update set email = excluded.email;

insert into storage.buckets (id, name, public)
values ('papers', 'papers', true)
on conflict (id) do nothing;

drop policy if exists "public read files" on storage.objects;
drop policy if exists "admin manage files" on storage.objects;
drop policy if exists "admin insert files" on storage.objects;
drop policy if exists "admin update files" on storage.objects;
drop policy if exists "admin delete files" on storage.objects;
create policy "public read files" on storage.objects
  for select to anon, authenticated using (bucket_id = 'papers');
create policy "admin insert files" on storage.objects
  for insert to authenticated with check (bucket_id = 'papers' and private.is_admin());
create policy "admin update files" on storage.objects
  for update to authenticated
  using (bucket_id = 'papers' and private.is_admin())
  with check (bucket_id = 'papers' and private.is_admin());
create policy "admin delete files" on storage.objects
  for delete to authenticated using (bucket_id = 'papers' and private.is_admin());

commit;
