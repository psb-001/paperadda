-- ============================================================
-- PYQ Hub backend setup — Supabase (Postgres)
-- HOW TO RUN: Supabase Dashboard → SQL Editor → New query
--              → paste this whole file → Run. Done in ~10 seconds.
-- ============================================================

-- ---------- tables ----------
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
  content text not null default '', -- optional short list description (not the PDF body)
  subject_name text not null default '',
  branch_code text not null default '',
  academic_year int not null default 1,
  file_url text not null default '', -- legacy public URL; unused when storage_path set
  storage_path text not null default '', -- key into the `papers` storage bucket; empty = no PDF yet
  updated_at timestamptz not null default now()
);

-- ---------- auto-update updated_at ----------
create or replace function touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists trg_papers_updated on papers;
create trigger trg_papers_updated
  before update on papers
  for each row execute function touch_updated_at();

drop trigger if exists trg_notes_updated on notes;
create trigger trg_notes_updated
  before update on notes
  for each row execute function touch_updated_at();

-- ---------- Row Level Security ----------
-- World (app users, anon key): READ ONLY.
-- Admin (logged in via portal): full write. Only the admin
-- account you create manually can log in (disable public
-- sign-ups in Dashboard → Authentication → Sign In/Up).
alter table subjects enable row level security;
alter table papers enable row level security;
alter table notes enable row level security;

drop policy if exists "public read" on subjects;
create policy "public read" on subjects for select using (true);
drop policy if exists "public read" on papers;
create policy "public read" on papers for select using (true);
drop policy if exists "public read" on notes;
create policy "public read" on notes for select using (true);

drop policy if exists "admin write" on subjects;
create policy "admin write" on subjects
  for all using ((auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me')
  with check ((auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me');
drop policy if exists "admin write" on papers;
create policy "admin write" on papers
  for all using ((auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me')
  with check ((auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me');
drop policy if exists "admin write" on notes;
create policy "admin write" on notes
  for all using ((auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me')
  with check ((auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me');

-- ---------- Storage bucket for PDFs ----------
insert into storage.buckets (id, name, public)
values ('papers', 'papers', true)
on conflict (id) do nothing;

drop policy if exists "public read files" on storage.objects;
create policy "public read files" on storage.objects
  for select using (bucket_id = 'papers');

drop policy if exists "admin manage files" on storage.objects;
create policy "admin manage files" on storage.objects
  for all using (bucket_id = 'papers' and (auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me')
  with check (bucket_id = 'papers' and (auth.jwt() ->> 'email') = 'chillmaaryaaar@proton.me');
