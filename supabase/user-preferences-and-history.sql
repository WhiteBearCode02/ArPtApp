-- Cloud reminder preferences and append-only body measurement history.
create table if not exists public.user_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    reminder_enabled boolean not null default true,
    reminder_hour smallint not null default 20 check (reminder_hour between 0 and 23),
    reminder_minute smallint not null default 0 check (reminder_minute between 0 and 59),
    timezone text not null default 'Asia/Seoul' check (char_length(timezone) between 1 and 64),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

alter table public.user_preferences enable row level security;
revoke all on table public.user_preferences from anon, authenticated;
grant select, insert, update on table public.user_preferences to authenticated;

drop policy if exists "users select own preferences" on public.user_preferences;
drop policy if exists "users insert own preferences" on public.user_preferences;
drop policy if exists "users update own preferences" on public.user_preferences;
create policy "users select own preferences" on public.user_preferences
for select to authenticated using ((select auth.uid()) = user_id);
create policy "users insert own preferences" on public.user_preferences
for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "users update own preferences" on public.user_preferences
for update to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create table if not exists public.body_measurements (
    id bigint generated always as identity primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    measured_at timestamptz not null default timezone('utc', now()),
    height_cm real check (height_cm is null or height_cm between 80 and 250),
    weight_kg real check (weight_kg is null or weight_kg between 20 and 350),
    skeletal_muscle_mass_kg real
        check (skeletal_muscle_mass_kg is null or skeletal_muscle_mass_kg between 0 and 200),
    body_fat_mass_kg real check (body_fat_mass_kg is null or body_fat_mass_kg between 0 and 350),
    body_fat_percentage real check (body_fat_percentage is null or body_fat_percentage between 0 and 75)
);

create index if not exists body_measurements_user_measured_idx
    on public.body_measurements (user_id, measured_at desc);
alter table public.body_measurements enable row level security;
revoke all on table public.body_measurements from anon, authenticated;
grant select on table public.body_measurements to authenticated;
drop policy if exists "users read own body measurements" on public.body_measurements;
create policy "users read own body measurements" on public.body_measurements
for select to authenticated using ((select auth.uid()) = user_id);

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.set_row_updated_at()
returns trigger language plpgsql security invoker set search_path = '' as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

create or replace function private.capture_body_measurement()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if (
        tg_op = 'INSERT' and (
            new.height_cm is not null or new.weight_kg is not null
            or new.skeletal_muscle_mass_kg is not null
            or new.body_fat_mass_kg is not null or new.body_fat_percentage is not null
        )
    ) or (
        tg_op = 'UPDATE' and (
            new.height_cm is distinct from old.height_cm
            or new.weight_kg is distinct from old.weight_kg
            or new.skeletal_muscle_mass_kg is distinct from old.skeletal_muscle_mass_kg
            or new.body_fat_mass_kg is distinct from old.body_fat_mass_kg
            or new.body_fat_percentage is distinct from old.body_fat_percentage
        )
    ) then
        insert into public.body_measurements (
            user_id, height_cm, weight_kg, skeletal_muscle_mass_kg,
            body_fat_mass_kg, body_fat_percentage
        ) values (
            new.user_id, new.height_cm, new.weight_kg, new.skeletal_muscle_mass_kg,
            new.body_fat_mass_kg, new.body_fat_percentage
        );
    end if;
    return new;
end;
$$;

revoke all on function private.set_row_updated_at() from public, anon, authenticated;
revoke all on function private.capture_body_measurement() from public, anon, authenticated;
drop trigger if exists set_user_preferences_updated_at on public.user_preferences;
create trigger set_user_preferences_updated_at before update on public.user_preferences
for each row execute function private.set_row_updated_at();
drop trigger if exists capture_user_profile_body_measurement on public.user_profiles;
create trigger capture_user_profile_body_measurement
after insert or update of height_cm, weight_kg, skeletal_muscle_mass_kg,
    body_fat_mass_kg, body_fat_percentage on public.user_profiles
for each row execute function private.capture_body_measurement();

insert into public.body_measurements (
    user_id, height_cm, weight_kg, skeletal_muscle_mass_kg,
    body_fat_mass_kg, body_fat_percentage
)
select p.user_id, p.height_cm, p.weight_kg, p.skeletal_muscle_mass_kg,
       p.body_fat_mass_kg, p.body_fat_percentage
from public.user_profiles p
where (
    p.height_cm is not null or p.weight_kg is not null
    or p.skeletal_muscle_mass_kg is not null
    or p.body_fat_mass_kg is not null or p.body_fat_percentage is not null
)
and not exists (select 1 from public.body_measurements m where m.user_id = p.user_id);
