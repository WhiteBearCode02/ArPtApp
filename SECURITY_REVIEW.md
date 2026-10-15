# 보안 수정 기록

기준일: 2026-10-15. 로컬 소스·테스트·새 빌드 검증과 원격 운영 검증을 구분합니다.
법률 자문, 침투 테스트 또는 전체 dependency CVE 감사 완료 보고서가 아닙니다.

## 구현한 방어

| 경계 | 구현 | 제한 |
|---|---|---|
| Android 백업 | `allowBackup=false`; legacy backup/cloud-backup/device-transfer의 credential/device-protected 저장 영역 전체 제외 | 이미 생성된 외부 백업을 삭제하지 않음. OEM 실기기 복원 테스트 필요 |
| 비밀번호 복구 | 정확한 URI/경로, 중복·오류·미지원 파라미터, 누락 토큰, 만료, issuer/audience/role/복구 AMR 검사. 입력 access token을 Auth로 검증하고 refresh token을 서버에서 회전·계정/세션 결합 확인 후 import | 단순 JWT decode는 인증이 아님. 검증된 HTTPS App Link/PKCE 전환은 별도 과제 |
| 복구 권한 | 별도 메모리 전용 Auth client + repository별 capability, 최대 10분·계정/세션 결합, 성공/취소 후 폐기. 기존 로그인은 근거로 사용/덮어쓰기하지 않음 | Auth hook이 recovery AMR을 제거한 프로젝트는 안전하게 거부. 서버 설정 변경 안 함 |
| 신규 회원정보 | Auth metadata allowlist는 `name`만. 신체 수치는 no-backup 임시 draft → 인증 후 `user_profiles`에 저장 | 과거 Auth metadata는 변경/삭제하지 않음. 이메일 인증 전 다른 기기로 옮기면 draft는 전송되지 않음 |
| YouTube | 사용자 access JWT를 헤더로 전달. gateway `verify_jwt=true` + `/auth/v1/user` 검증, 익명 사용자 차단, 요청당 512B·12초 제한 | 함수 소스만 수정, 실제 배포 안 함 |
| YouTube 호출 제한 | 캐시 적중을 포함해 계정당 UTC 분 6회·일 120회, DB 원자적 제한. 실패/미적용 시 503, 초과 429. 기존 YouTube 검색 전역 일 30회·3시간 캐시 유지 | SQL 미적용. 분 경계 burst·다계정·Auth/gateway에 도달하는 요청 자체의 사용량까지 없애는 장치 아님 |
| 모델 배포 | 모든 빌드 유형에서 loose `.tflite/.pt/.pth/.onnx` assets 제외. MediaPipe `.task` 유지. 실제 APK 검사 스크립트 제공 | 원본 모델 파일과 Git 추적/과거 APK는 그대로. 라이선스 검토 완료가 아님 |

## 하지 않은 작업

- 원격 DB migration/SQL 실행, Edge Function 배포, Auth 설정 변경, billing 변경.
- 모델 삭제/이동, Git 추적 해제/이력 재작성, commit/push, LLM provider 연결.
- 기존 계정 metadata 정리, 실제 사용자 정보 조회, 실제 인증·YouTube·LLM API 호출.

## 운영 전 남은 검증

1. 별도 승인 후 `supabase/youtube-ranking.sql`의 신규 private quota/RPC 적용 및 advisors/실제 동시성·RLS 테스트.
2. gateway JWT 검증, 정상/만료/익명 JWT, missing RPC/429, cache hit 제한을 배포 환경에서 검증.
3. 실제 이메일 복구 링크(복구 AMR 포함)와 기기 회전·새 링크·취소·성공 후 재사용, Android 백업/복원 확인.
4. 과거 신체 Auth metadata는 서버에서 안전한 이관/삭제 절차를 별도 승인 후 수행. 새 앱이 과거 JWT를 정리하지는 않음.
5. 개인정보처리방침·민감정보/국외 이전 검토·회원탈퇴와 데이터 삭제 기능, 정식 LICENSE/NOTICE는 공개 서비스 전 별도 완료.
6. 실제 Supabase 요금제/부가기능/외부 SMTP 확인. Free plan 사용 중 미청구라는 서비스 정책과 코드의 0원 보장은 다름.

검증 명령과 이번 실행 결과는 [RELEASE_READINESS.md](RELEASE_READINESS.md), 데이터 경로는 [PRIVACY_DATA_MAP.md](PRIVACY_DATA_MAP.md)를 참조하세요.

## 변경 파일 전체 목록

수정 18개, 신규 15개, 총 33개입니다. 모델 원본·기존 SQL 적용 상태·LLM 기능은 변경하지 않았습니다.

```text
README.md
SECURITY_REVIEW.md (신규)
PRIVACY_DATA_MAP.md (신규)
RELEASE_READINESS.md (신규)
docs/DEVICE_VALIDATION.md
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
app/src/main/java/com/example/arptapp/HomeActivity.kt
app/src/main/java/com/example/arptapp/PasswordResetActivity.kt
app/src/main/java/com/example/arptapp/core/auth/PasswordRecoveryPolicy.kt (신규)
app/src/main/java/com/example/arptapp/data/preferences/PendingSignUpProfileRepository.kt (신규)
app/src/main/java/com/example/arptapp/data/repository/SignUpProfileSync.kt (신규)
app/src/main/java/com/example/arptapp/data/remote/SupabaseAuthRepository.kt
app/src/main/java/com/example/arptapp/data/remote/SupabaseClientProvider.kt
app/src/main/java/com/example/arptapp/data/remote/UserProfileRepository.kt
app/src/main/java/com/example/arptapp/data/remote/YouTubeRankingRepository.kt
app/src/main/java/com/example/arptapp/viewmodel/LoginViewModel.kt
app/src/main/java/com/example/arptapp/viewmodel/PasswordRecoveryViewModel.kt (신규)
app/src/test/java/com/example/arptapp/core/auth/PasswordRecoveryPolicyTest.kt (신규)
app/src/test/java/com/example/arptapp/data/repository/SignUpProfileSyncTest.kt (신규)
scripts/verify-coaching-security.mjs
scripts/security-hardening.test.mjs (신규)
scripts/verify-public-apk.ps1 (신규)
supabase/config.toml
supabase/youtube-ranking.sql
supabase/functions/youtube-ranking/README.md
supabase/functions/youtube-ranking/index.ts
supabase/functions/youtube-ranking/handler.ts (신규)
supabase/functions/youtube-ranking/ranking-service.ts (신규, 기존 index의 조회·캐시 로직 분리)
supabase/functions/youtube-ranking/handler.test.ts (신규)
supabase/functions/youtube-ranking/entrypoint.test.ts (신규)
```

Supabase 지침의 최소 권한 원칙에 따라 quota definer는 private schema에 두고, public RPC는 invoker로 제한합니다.
요청에서 계정 ID/시간/한도를 받지 않으며 인증된 사용자만 자신의 카운터를 예약할 수 있습니다.
비밀번호 변경 후 로그아웃은 해당 복구 계정에 한해 best-effort로 요청합니다. 네트워크 실패 시 서버 세션 취소를
보장하지 않으며, 로그아웃이 성공해도 기존 access JWT가 즉시 무효가 된다고 간주하지 않습니다.

## 공식 근거

- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Supabase JWT claims: recovery AMR](https://supabase.com/docs/guides/auth/jwt-fields)
- [Supabase Edge Function 인증](https://supabase.com/docs/guides/functions/auth)
- [Supabase 비용 제어](https://supabase.com/docs/guides/platform/cost-control), [오류 응답을 포함하는 Edge 호출 사용량](https://supabase.com/docs/guides/platform/manage-your-usage/edge-function-invocations)
- [Google Play 사용자 데이터 정책](https://support.google.com/googleplay/android-developer/answer/10144311?hl=ko)
- 모델/데이터 권리 상태: [DATASET_LICENSES.md](DATASET_LICENSES.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
