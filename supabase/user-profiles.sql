-- Private body profile for each Supabase Auth user.
create table if not exists public.user_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    nickname text not null default '',
    height_cm real,
    weight_kg real,
    skeletal_muscle_mass_kg real,
    body_fat_mass_kg real,
    body_fat_percentage real,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint user_profiles_nickname_length check (char_length(nickname) <= 20),
    constraint user_profiles_height_range check (height_cm is null or height_cm between 80 and 250),
    constraint user_profiles_weight_range check (weight_kg is null or weight_kg between 20 and 350),
    constraint user_profiles_muscle_range check (skeletal_muscle_mass_kg is null or skeletal_muscle_mass_kg between 0 and 200),
    constraint user_profiles_fat_mass_range check (body_fat_mass_kg is null or body_fat_mass_kg between 0 and 350),
    constraint user_profiles_fat_percentage_range check (body_fat_percentage is null or body_fat_percentage between 0 and 75)
);

alter table public.user_profiles enable row level security;
revoke all on table public.user_profiles from anon, authenticated;
grant select, insert, update on table public.user_profiles to authenticated;

drop policy if exists "users_select_own_profile" on public.user_profiles;
create policy "users_select_own_profile" on public.user_profiles
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "users_insert_own_profile" on public.user_profiles;
create policy "users_insert_own_profile" on public.user_profiles
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "users_update_own_profile" on public.user_profiles;
create policy "users_update_own_profile" on public.user_profiles
    for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.profile_number(metadata jsonb, key_name text)
returns real language sql immutable set search_path = '' as $$
    select case
        when metadata ->> key_name ~ '^-?([0-9]+([.][0-9]*)?|[.][0-9]+)$'
            then (metadata ->> key_name)::real
        else null
    end
$$;

create or replace function private.handle_new_user_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.user_profiles (
        user_id, nickname, height_cm, weight_kg, skeletal_muscle_mass_kg,
        body_fat_mass_kg, body_fat_percentage
    ) values (
        new.id,
        left(coalesce(new.raw_user_meta_data ->> 'name', ''), 20),
        private.profile_number(new.raw_user_meta_data, 'height_cm'),
        private.profile_number(new.raw_user_meta_data, 'weight_kg'),
        private.profile_number(new.raw_user_meta_data, 'skeletal_muscle_mass_kg'),
        private.profile_number(new.raw_user_meta_data, 'body_fat_mass_kg'),
        private.profile_number(new.raw_user_meta_data, 'body_fat_percentage')
    ) on conflict (user_id) do nothing;
    return new;
end;
$$;

create or replace function private.set_user_profile_updated_at()
returns trigger language plpgsql security invoker set search_path = '' as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

revoke all on function private.profile_number(jsonb, text) from public, anon, authenticated;
revoke all on function private.handle_new_user_profile() from public, anon, authenticated;
revoke all on function private.set_user_profile_updated_at() from public, anon, authenticated;

drop trigger if exists on_auth_user_profile_created on auth.users;
create trigger on_auth_user_profile_created
    after insert on auth.users
    for each row execute function private.handle_new_user_profile();

drop trigger if exists set_user_profile_updated_at on public.user_profiles;
create trigger set_user_profile_updated_at
    before update on public.user_profiles
    for each row execute function private.set_user_profile_updated_at();

-- Backfill users created before this table existed from their sign-up metadata.
insert into public.user_profiles (
    user_id, nickname, height_cm, weight_kg, skeletal_muscle_mass_kg,
    body_fat_mass_kg, body_fat_percentage
)
select
    id,
    left(coalesce(raw_user_meta_data ->> 'name', ''), 20),
    private.profile_number(raw_user_meta_data, 'height_cm'),
    private.profile_number(raw_user_meta_data, 'weight_kg'),
    private.profile_number(raw_user_meta_data, 'skeletal_muscle_mass_kg'),
    private.profile_number(raw_user_meta_data, 'body_fat_mass_kg'),
    private.profile_number(raw_user_meta_data, 'body_fat_percentage')
from auth.users
on conflict (user_id) do nothing;
