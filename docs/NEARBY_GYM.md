# 주변 헬스장 · 외부 지도 연결

HomeActivity의 **내 주변 헬스장 → 주변 헬스장 찾기**에서 외부 NAVER 지도 검색으로 연결합니다.
앱 내부에 업체 목록을 가져오거나 저장·캐싱·정렬하지 않습니다. NAVER와의 공식 제휴를 의미하지 않습니다.

## 동작과 제한

1. Home 진입 시에는 위치 조회·권한 요청·지도 실행이 없습니다.
2. 버튼 클릭 시 Activity Result API로 FINE/COARSE 권한을 요청합니다. 대략적인 위치만 허용해도 사용할 수 있습니다.
3. Android 기본 LocationManager의 위치 서비스/활성 제공자를 확인하고 2분 이내 lastKnownLocation을 사용합니다.
   없거나 오래되었으면 기존 AndroidX Core의 LocationManagerCompat.getCurrentLocation으로 일회성 위치를 요청합니다.
   network/GPS 중 최초 유효 결과만 사용하고 모든 요청을 해제합니다. 전체 대기는 15초로 제한합니다.
   대략적인 위치 권한만 허용한 경우 network만 사용하며, 해당 제공자가 없으면 실패 안내를 표시합니다.
4. 위치 확인에 성공하면 NAVER 지도 앱의 `nmap://search?query=…&appname=…`을 우선 실행합니다.
5. 앱이 없거나 실행이 차단되면 `https://map.naver.com/p/search/…`를 처리하는 브라우저/외부 앱으로 연결합니다.
   모두 실행 불가하면 메시지를 표시하며 기존 Home 기능은 계속 사용할 수 있습니다.

**좌표로 검색 중심이 자동 설정되지는 않습니다.** 공식 `/search` 문서에는 좌표 파라미터가 없습니다.
임의의 lat/lng 파라미터를 만들거나 지도 앱을 두 번 실행하는 대신, “헬스장” 검색을 열고
**NAVER 지도 안에서 현재 위치 버튼을 누르도록** 카드와 메시지로 안내합니다.
NAVER 지도/브라우저가 자신의 위치 권한을 별도로 요청할 수 있습니다.

- [NAVER 공식 앱 딥링크 문서](https://guide.ncloud-docs.com/docs/application-maps-url-scheme-vpc)
- [NAVER 웨일 담당자가 안내한 웹 검색 URL](https://forum.whale.naver.com/topic/57381/)
- [Android LocationManager 문서](https://developer.android.com/reference/android/location/LocationManager)
- [AndroidX LocationManagerCompat 문서](https://developer.android.com/reference/androidx/core/location/LocationManagerCompat)

## 비용·보안·개인정보

- NAVER Search API/API HUB/Maps SDK/Dynamic Map API, Edge Function, DB, API 키, 결제 설정은 추가하지 않습니다.
- Google Play Services Location SDK를 제거하고 Android 기본 LocationManager로 대체했습니다.
  기존 AndroidX Core를 재사용하므로 새 의존성·API 키·서비스 가입을 추가하지 않습니다.
- 이 기능이 개발자에게 호출하는 과금 대상 외부 API는 없습니다. 휴대폰의 일반 데이터 통신료는 별개입니다.
  기존 Supabase/YouTube 등 다른 기능의 서비스 요금까지 무료로 보장한다는 의미는 아닙니다.
- FINE/COARSE만 추가하고 BACKGROUND_LOCATION, 위치 서비스/Worker/알람을 추가하지 않습니다.
- 위치 객체는 helper 안에서 일시적으로 확인하고 외부에는 READY/실패 상태만 반환합니다.
  Room/Supabase/Preferences/DataStore/Intent/로그/분석 이벤트/저장 상태에 좌표를 넣지 않습니다.
- 좌표를 AIRPTCoach 서버나 NAVER 링크에 전달하지 않습니다. OS 위치 제공자의 내부 처리는
  해당 서비스 정책을 따르고, 외부 지도의 위치 처리는 사용자가 외부 앱에서 허용한 권한을 따릅니다.
- 네이티브 딥링크는 NAVER 지도 패키지를 명시합니다. 웹은 고정된 HTTPS NAVER 주소만 사용합니다.
- `startActivity`의 ActivityNotFoundException과 SecurityException을 처리합니다.
- 중복 클릭을 막고 Home이 백그라운드로 이동하면 위치 요청을 취소합니다. 재진입 시 자동 재요청하지 않습니다.
  위치 권한 팝업 중 화면이 재생성되면 자동 검색 없이 버튼을 다시 눌러 사용할 수 있습니다.
- GPS OFF, 활성 제공자 없음, 위치 null/실패/timeout, 권한 거부는 안내만 표시합니다.
  인터넷이 없으면 외부 지도가 데이터를 표시하지 못할 수 있지만 AIRPTCoach의 다른 기능을 막지는 않습니다.

## 파일 책임

| 파일 | 역할 |
|---|---|
| `ui/gym/NearbyGymController.kt` | 버튼·Activity Result·로딩·lifecycle 연결 |
| `ui/gym/NearbyGymLocationHelper.kt` | 권한/위치 서비스 확인 및 한 번의 위치 요청 |
| `ui/gym/NearbyGymPolicy.kt` | 위치 유효성·중복 요청·권한 상태의 순수 정책 |
| `ui/gym/NearbyGymProviderPolicy.kt` | 권한별 OS 제공자 선택 및 최초 유효 결과/취소 후 콜백 정책 |
| `ui/gym/NearbyGymNavigation.kt` | 순수 URL 생성 및 앱→웹 fallback 정책 |
| `ui/gym/NearbyGymNavigator.kt` | 안전한 Android Intent 실행 |
| `values/strings_nearby_gym.xml` | 한국어 문구 |
| `ui/gym/NearbyGymTest.kt` (test) | 정책 및 URL/fallback JVM 테스트 |
| `ui/gym/NearbyGymProviderPolicyTest.kt` (test) | 제공자 선택·권한 철회·중복/늦은 콜백 JVM 테스트 |

초기 기능 추가 시 변경한 기존 파일은 `HomeActivity.kt`, `activity_home.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`, `README.md`입니다.
LocationManager 교체에서는 위치 helper/정책/테스트와 Gradle·문서만 변경하며 UI·지도 Intent·Manifest는 유지합니다.
운동 분석·로그인·Room·Supabase·YouTube 코드와 서버 설정은 변경하지 않습니다.

## 검증

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Windows에서 IDE가 생성 파일을 잠근 경우 기존 프로젝트 설정의 `-PverificationBuildDir=nearby-map-verification`으로
검증 출력 디렉터리만 분리할 수 있습니다. 이때 APK는 `build/nearby-map-verification/outputs/apk/debug/app-debug.apk`입니다.

JVM 테스트는 대략적/정확한 권한, 권한 대기·거부, 중복 클릭, stop/재생성 정책, 위치 유효성·시각,
공식 URI 파라미터, URL 인코딩, 앱 우선 실행, 웹 fallback, 모든 연결 실패를 검증합니다.
실제 OS 위치 제공자·권한 팝업·NAVER 앱/웹 화면 동작은 아래 실기기 확인이 필요합니다.

| 실기기 확인 | 기대 동작 |
|---|---|
| Home 진입/재진입 | 자동 권한 팝업·위치 요청 없음 |
| 권한 없음에서 클릭 | FINE/COARSE 팝업, 위치 권한 거부해도 Home 계속 사용 |
| 대략적인 위치만 허용 | 정상 위치 확인 시 지도 검색 연결 |
| 위치 OFF | 위치 서비스를 켜달라는 안내, 버튼 복구 |
| 위치 null·수신 실패·timeout | 최대 15초 후 안내, 버튼 복구 |
| 지도 앱 설치 | NAVER 지도 “헬스장” 검색, 현재 위치 사용 안내 |
| 지도 앱 미설치 | NAVER 지도 웹 검색 |
| 브라우저/지도 실행 차단 | 실패 안내, 크래시 없음 |
| 연타·위치 확인 중 Home 이탈·회전 | 중복 요청 방지·요청 취소·재진입 자동 실행 없음 |
| 네트워크 OFF | 위치 실패 안내 또는 외부 지도 연결, 기존 Home 버튼 사용 가능 |
| 기존 기능 | 로그인→Home, 트레이닝 시작, 기록/설정, YouTube 화면 회귀 확인 |

### LocationManager 교체 검증 결과 (2026-10-13)

- `:app:assembleDebug :app:testDebugUnitTest :app:dependencyInsight --dependency play-services --configuration debugRuntimeClasspath`: **BUILD SUCCESSFUL**.
- 검증 출력 폴더는 `-PverificationBuildDir=location-manager-verification`으로 기존 IDE 출력과 분리했습니다.
- Windows에서는 `--max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'`로 검증했습니다. 프로젝트의 영구 JVM/의존성 설정은 바꾸지 않았습니다.
- 전체 JVM 테스트 121개 통과(실패/오류/skip 0), 이번 제공자/완료 정책 테스트 10개 포함.
- debugRuntimeClasspath에서 play-services에 일치하는 의존성 없음. 소스의 GMS 참조도 없음.
- 병합 Manifest의 위치 권한은 FINE/COARSE만 확인했습니다. BACKGROUND_LOCATION 없음.
- APK: `build/location-manager-verification/outputs/apk/debug/app-debug.apk`. 현재 로컬 assets의 모델이 포함될 수 있으므로 Dataset A(RickyRiccio similar_dataset) 학습 weight 재배포 검토 전 외부 배포하지 않습니다. AI-Hub Dataset B는 YOLO EXP02 학습 데이터가 아닙니다.
- UI·지도 Intent·Manifest 원본은 변경하지 않았습니다. 위치의 앱 서버 전송/저장·좌표 로그·새 유료 API 없음.
- 기존 TensorFlow Lite namespace/네이티브 strip 및 Gradle deprecation 경고는 별도 정리 대상입니다.
- 연결된 기기가 없어 실제 GPS/권한 거부/대략적 위치/지도 실행 검증은 미수행입니다.

### 이전 구현 검증 결과 (2026-10-03)

- `:app:assembleDebug :app:testDebugUnitTest --no-daemon --no-problems-report -PverificationBuildDir=nearby-map-verification`: **BUILD SUCCESSFUL**.
- 전체 JVM 테스트 **41개 통과**: 기존 24개 + 신규 NearbyGymTest 17개. 실패/오류/skip 0개.
- APK: `build/nearby-map-verification/outputs/apk/debug/app-debug.apk`.
- `git diff --check` 통과. 생성 APK/검증 출력은 기존 `.gitignore`의 `/build`로 제외됩니다.
- 새 helper에 네트워크 호출·인증 키·좌표 로깅·영구 저장 코드가 없음을 확인했습니다.
- 병합 Manifest를 이전 빌드와 비교해 FINE/COARSE만 새로 추가됨을 확인했습니다. BACKGROUND_LOCATION 없음.
- 기존 운동·로그인·YouTube·Room·Supabase 구현 파일은 변경하지 않았으며 관련 기존 JVM 테스트는 통과했습니다.
  실제 로그인·서버 동기화·카메라 화면의 end-to-end 성공까지 의미하지는 않습니다.
- 기존 TensorFlow Lite namespace/native stripping 및 Gradle deprecation 경고가 있습니다. 빌드 실패는 아닙니다.
- `adb devices`에 연결된 기기가 없어 실제 권한 팝업·GPS·NAVER 앱/웹 동작 및 기존 화면 회귀 테스트는 미실행입니다.
  위 실기기 체크리스트를 사용해 확인해야 합니다.
