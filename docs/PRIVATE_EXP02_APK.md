# 본인 기기 테스트 전용 EXP02 포함 APK

2026-11-09 사용자 승인 범위: 본인 소유 휴대폰/태블릿에 설치하는 개인 DEBUG 테스트.
공개 배포 승인이나 전체 라이선스 적합성 보증이 아닙니다. 기존 기본 APK와 구분합니다.

## 빌드와 로딩 경계

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --no-daemon --no-build-cache --max-workers=2 '-PverificationBuildDir=private-exp02-verification' '-PprivateExp02Apk=true'
```

- 기본값은 모델 미포함입니다. 위 옵션은 debug source set에만 생성 자산을 연결합니다.
- EXP02 원본은 Git ignored 위치에 그대로 두고, 생성된 자산도 ignored build 폴더에만 만듭니다.
- SHA-256/크기를 빌드 시 검증한 모델 하나를 `assets/yolo_private/exp02.bin`으로 포함합니다.
- 이름 변경은 포맷 변환이나 공개 검사 우회가 아닙니다. 기존 공개 APK 검사는 `assets/*yolo*`를 계속 거부합니다.
- 개인용 DEBUG는 고정 asset을 직접 memory-map하고 크기/SHA를 다시 검사합니다. 기기 파일 주입/모델 다운로드가 필요 없습니다.
- 일반 DEBUG는 기존 noBackup private 파일만 사용합니다. release/A-B는 bundled flag=false, 개인용 생성 자산 source set을 사용하지 않습니다.
- package/signing/version, 로그인, DB, MediaPipe, 횟수/점수, YOLO Shadow 정책은 바꾸지 않습니다.
- 개인용 APK를 설치하면 동일 서명의 기존 기본 앱을 업데이트합니다. 서명 충돌 시 앱을 삭제하지 말고 중단하세요.

## 출처와 이용 조건

사용자 제공 학습 데이터: AI-Hub **크로스핏 동작 데이터**, 71422.
https://aihub.or.kr/aihubdata/data/view.do?dataSetSn=71422

AI-Hub 공식 FAQ는 데이터 출처를 명시한 학습 결과 모델/서비스 활용·배포와 원본 데이터 재배포를 구분합니다.
https://aihub.or.kr/aihubnews/faq/list.do
https://aihub.or.kr/intrcn/guid/usagepolicy.do?currMenu=151&topMenu=105

Ultralytics 소프트웨어/모델 조건은 별도입니다. 본인 기기 사용과 타인 배포를 구분하며,
공개 배포 전에는 대응 소스 제공·고지·의존성·모델 권리 조건을 별도로 검토해야 합니다.
https://www.ultralytics.com/license
https://www.gnu.org/licenses/gpl-faq.en.html#PropagationNotConveying

로컬 Ultralytics 8.4.176 패키지의 AGPL-3.0 전문을 `licenses/ULTRALYTICS_AGPL-3.0.txt`에 보존하고,
데이터 출처/사용 범위 고지와 함께 개인용 APK에 포함합니다. 원본 사진/라벨/검증 프레임은 포함하지 않습니다.
전체 앱 라이선스를 임의 변경하거나 유료 계약/서비스를 추가하지 않습니다.

## 공개 방지 / 검증

```powershell
powershell -File scripts/verify-private-exp02-apk.ps1 -ApkPath <개인용APK>
powershell -File scripts/verify-public-apk.ps1 -ApkPath <공개검사대상APK>
```

개인용 APK는 첫 검사를 통과해야 하고, 두 번째 공개 검사는 **실패해야 정상**입니다.
모델 원본과 APK를 GitHub/Release/공유 링크에 올리지 마세요.
YOLO는 여전히 Shadow이므로 앱의 주 운동 결정이나 횟수 계산의 Primary로 전환하지 않습니다.
태블릿 실기기 실행은 APK 빌드 성공과 별개로 확인해야 합니다.

## 이번 요청으로 수정한 파일

- `app/build.gradle.kts` — 명시적 개인용 옵션/생성 자산/DEBUG flag. 기본 제외 정책 유지.
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt` — DEBUG 한정 고정 asset mmap + identity 검증.
- `scripts/yolo-session-wiring.test.mjs` — 일반 파일 경로/명시적 개인용 경로 분리 회귀 검사.
- `scripts/private-exp02-apk.test.mjs` — 개인용 빌드·런타임 경계 검사 (신규).
- `scripts/verify-private-exp02-apk.ps1` — 실제 APK 모델 해시·출처/라이선스 전문 검사 (신규).
- `licenses/ULTRALYTICS_AGPL-3.0.txt` — 설치된 Ultralytics 패키지의 라이선스 전문 보존 (신규).
- `docs/private-exp02-apk-notice.txt` — APK 동봉 출처/사용 범위 고지 (신규).
- `docs/PRIVATE_EXP02_APK.md` — 빌드 방법/권리·배포 경계/검증 결과 (신규).
- `docs/model-selection-exp02.md` — 기존 기본 APK와 별도 개인용 예외를 구분.
- `THIRD_PARTY_NOTICES.md` — 사용자 확인 출처와 개인용 고지 보완.

기존 작업 트리의 다른 변경은 되돌리지 않았으며, 자동 commit/push/기기 설치는 하지 않습니다.

## 생성 APK (2026-11-09)

- 전달용: `build/private-exp02-verification/APK/AIRPTCoach-EXP02-Personal-debug.apk`
- 원본: `build/private-exp02-verification/outputs/apk/debug/app-debug.apk`
- 크기: **46,612,709 bytes**
- SHA-256: `3e02e7cec1fdad307c22ff99e8e937b74ad01a952ae9817136d0beb52052ca14`
- 실제 package: `com.example.arptapp`, label: `AIRPTCoach`, versionCode `1`, versionName `1.0`.
- 실제 APK의 DEBUG 서명(v2) 검증 PASS, minSdk26 / targetSdk35.
- 모델 asset은 9,894,030 bytes, uncompressed로 포함되어 mmap 사용이 가능합니다.
- 개인용 APK 검사 PASS: 정확한 모델 SHA와 라이선스/출처 고지 확인.
- 기존 공개 APK 검사에서 **의도대로 차단됨**. 공개용 검사 조건을 완화하지 않았습니다.
- 기존 MediaPipe task와 OpenCV 고지가 유지됨을 실제 ZIP에서 확인했습니다.
- APK와 원본 모델은 Git ignored 상태입니다. APK는 받는 사람이 모델을 추출할 수 있으므로 외부 공유하지 마세요.
- 이번 APK의 태블릿 설치/native inference/카메라 실기기 검증은 **NOT_RUN**입니다. 요청 범위에 따라 APK만 생성합니다.

첫 통합 빌드는 `assembleDebug` 성공 후 Lint 생성 자산 의존성 검사에서 실패했습니다.
소스 디렉터리에 `builtBy`를 연결하고 DEBUG Lint 작업의 명시적 의존성을 추가하여 수정했습니다.

수정 후 최종 통합 검증은 **BUILD SUCCESSFUL (7분 17초, 69 tasks)**입니다.

| 검증 | 결과 |
|---|---|
| DEBUG APK 빌드 | PASS |
| JVM 단위 테스트 | 66 suites / 671 tests, 실패·오류·skip 0 |
| Node 회귀 테스트 | 149 / 149 PASS |
| Android Lint | 오류·fatal 0 / 경고 298 |
| 개인용 APK 모델/고지 검사 | PASS |
| 개인용 APK 공개 검사 차단 | PASS (공개 검사에서 실패하는 것이 정상) |
| release / exp02Debug / exp03Debug 자산 병합 | 개인용 옵션을 켜도 모두 모델 미포함, 각 BuildConfig bundled flag=false 확인 |
| 변경 public text 비밀정보 패턴 검사 | 25개 PASS. 전체 Git 이력 검사는 수행하지 않음 |
| 태블릿 설치 / native 추론 | NOT_RUN (APK 생성까지만 요청) |

Lint 보고서: `build/private-exp02-verification/reports/lint-results-debug.html`.
release/A-B는 자산/BuildConfig 경계만 확인했으며 이번에 해당 variant 전체 APK를 만들거나 실기기 실행하지 않았습니다.
새 유료 API·다운로드·원격 SQL·배포·billing 변경은 없습니다. 기존 앱 전체 서비스의 비용 0원을 보증한다는 뜻은 아닙니다.
