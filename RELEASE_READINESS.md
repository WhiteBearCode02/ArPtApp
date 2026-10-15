# 공개 배포 준비 상태

기준일: 2026-10-15. **코드 보강 완료와 공개 서비스 승인/법적 권리 확보는 다릅니다.**

2026-10-16 후속 작업: [YOLO Shadow 구조 검증](docs/YOLO_DEVICE_VALIDATION.md). 실제 prediction은 export contract 미확인으로 비활성이며, debug/release 모델 패키징 차단과 공개 보류 정책은 유지합니다. 아래 표/명령의 `security-hardening-verification` APK는 이전 보안 작업 결과이고 새 Shadow 작업 APK는 `build/yolo-shadow-verification/outputs/apk/debug/app-debug.apk`로 별도 생성합니다. 공개 배포 승인본이 아닙니다.

## 이번 변경

- [x] Android 백업/데이터 추출 방어, strict password recovery, 신규 신체 Auth metadata 금지.
- [x] 이메일 인증 후 신체 프로필 저장 경로 유지(no-backup 임시 draft).
- [x] YouTube 사용자 JWT 인증·DB 기반 분/일 호출 제한 코드·SQL 준비.
- [x] debug/release 공통 unreviewed loose model assets 제외 및 APK 검사 명령 제공.
- [x] 원격 적용/배포/결제 변경/모델 삭제/LLM 연결 없이 구현.

## 로컬 검증

실행 결과(2026-10-15):

| 검증 | 결과 |
|---|---|
| `assembleDebug testDebugUnitTest` | 오프라인 빌드 성공, 최종 실행 3분 58초(진행 출력 확인) |
| Android JVM | 34 suites / 213 tests / 실패·오류 0 |
| Node 서버·보안 회귀 | 50 tests / 실패 0 (모의 HTTP, 실제 외부 API 요청 없음) |
| 새 APK 검사 | 두 YOLO/loose weight 없음, `pose_landmarker_lite.task` 유지 |
| 원본 모델 | EXP02 9,894,030 bytes, 기존 YOLO11 3,380,912 bytes 유지. 삭제/이동 안 함 |
| 변경 파일 비밀키 패턴 | 통과. 값 출력 없이 현재 Supabase 키는 publishable 분류. Git 이력 미스캔 |
| SQL/RLS/동시성 | 소스 정적 점검만. 로컬 Postgres/원격 DB 실행 안 함 |
| 실기기·실제 이메일·원격 함수·서명 release/AAB | 미검증/미배포 |

처음 제한된 파일 시스템 권한으로 실행했을 때 SDK에 접근할 수 없어 중단됐으며, SDK 접근 권한으로
같은 오프라인 명령을 실행해 성공했습니다. `local.properties`는 수정하지 않았습니다.
dependency의 기존 namespace/native strip 경고는 남아 있으며 전체 dependency 감사 완료를 의미하지 않습니다.

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --no-problems-report -PverificationBuildDir=security-hardening-verification --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
node --experimental-strip-types --test scripts/security-hardening.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node scripts/verify-coaching-security.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/security-hardening-verification/outputs/apk/debug/app-debug.apk
```

새 debug APK: `build/security-hardening-verification/outputs/apk/debug/app-debug.apk`.
이전 `build/location-manager-verification/...` 등의 APK는 갱신/삭제하지 않았으며 모델이 포함된 예전 파일일 수 있습니다.
새 debug APK의 패키징 통과만으로 서명된 release/AAB 또는 법적 공개 승인을 받았다고 간주하지 않습니다.

## 공개 서비스 전 차단 항목

- [ ] 사용자 별도 승인 후 YouTube 신규 quota SQL 적용 → RLS/동시성/advisors 확인 → 함수 실제 배포.
- [ ] 정상/만료/익명 JWT, 429/503, 캐시 적중에도 적용되는 제한을 원격 환경에서 확인.
- [ ] 실기기 API 26/31+ 백업·복원/기기 전송과 실제 이메일 복구(AMR 포함)·취소·회전 검증.
- [ ] 실제 Supabase 요금제/Spend Cap 예외/부가기능/외부 SMTP 확인. 호출 제한은 0원 보장이 아님.
- [ ] 개인정보처리방침·보유기간·회원탈퇴/관련 데이터 삭제·필요 동의·국외 이전 요건 준비.
- [ ] 정식 LICENSE, 전체 원본 NOTICE, MediaPipe bundle 고지와 Dataset A/B·기존 추적 모델/템플릿 권리 확인.
- [ ] 과거 신체 Auth metadata의 안전한 정리 여부 결정(이번에 원격 변경 안 함).
- [ ] 실제 배포할 release APK/AAB를 별도로 빌드·검사·서명. debug APK 공개 배포 금지.

## 비용·권리 승인 경계

새 provider/키/요금제/SMTP/유료 계약은 연결하지 않았습니다. LLM은 계속 비활성입니다.
서버 변경 적용은 현재 앱을 배포하는 것만으로 이루어지지 않습니다. SQL/함수 배포와 관련 비용·데이터 처리 조건은
사용자 승인 후 별도 작업으로 진행해야 합니다. [보안 상세](SECURITY_REVIEW.md), [데이터 흐름](PRIVACY_DATA_MAP.md).
