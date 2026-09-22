-- InkPad storage policies
-- Run in Supabase SQL Editor once.

-- Enable Email provider in Authentication → Providers (default on).
-- Confirm email: optional — turn off under Authentication → Providers → Email if you want instant login.
--
-- Optional Google sign-in:
-- 1) Google Cloud Console → create OAuth clients:
--    - Web application (Client ID + Secret) → paste into Supabase Auth → Providers → Google
--    - Android (package com.personal.inkpad + your debug/release SHA-1)
-- 2) Put the Web Client ID in local.properties as GOOGLE_WEB_CLIENT_ID=...
-- 3) Rebuild the app.

insert into storage.buckets (id, name, public, file_size_limit)
values ('inkpad', 'inkpad', false, 524288000)
on conflict (id) do update set file_size_limit = excluded.file_size_limit;

drop policy if exists "inkpad_select_own" on storage.objects;
drop policy if exists "inkpad_insert_own" on storage.objects;
drop policy if exists "inkpad_update_own" on storage.objects;
drop policy if exists "inkpad_delete_own" on storage.objects;

create policy "inkpad_select_own"
on storage.objects for select
to authenticated
using (
  bucket_id = 'inkpad'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "inkpad_insert_own"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'inkpad'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "inkpad_update_own"
on storage.objects for update
to authenticated
using (
  bucket_id = 'inkpad'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'inkpad'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "inkpad_delete_own"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'inkpad'
  and (storage.foldername(name))[1] = auth.uid()::text
);
