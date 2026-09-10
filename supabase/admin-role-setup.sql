-- Supabase Dashboard > SQL Editor에서 관리자 계정에 한 번만 실행하세요.
-- 아래 이메일을 실제 관리자 계정 이메일로 바꾼 뒤 실행해야 합니다.
-- service_role 키를 Android 앱에 넣지 마세요.

update auth.users
set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb)
    || '{"role":"admin"}'::jsonb
where lower(email) = lower('YOUR_ADMIN_EMAIL@example.com');

-- 적용 결과 확인
select id, email, raw_app_meta_data ->> 'role' as app_role
from auth.users
where lower(email) = lower('YOUR_ADMIN_EMAIL@example.com');
