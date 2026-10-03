# 주변 헬스장 · 외부 지도 연결

HomeActivity의 **내 주변 헬스장 → 주변 헬스장 찾기**에서 외부 NAVER 지도 검색으로 연결합니다.
앱 내부에 업체 목록을 가져오거나 저장·캐싱·정렬하지 않습니다. NAVER와의 공식 제휴를 의미하지 않습니다.

## 동작과 제한

1. Home 진입 시에는 위치 조회·권한 요청·지도 실행이 없습니다.
2. 버튼 클릭 시 Activity Result API로 FINE/COARSE 권한을 요청합니다. 대략적인 위치만 허용해도 사용할 수 있습니다.
3. 위치 서비스가 켜져 있는지 확인하고 FusedLocationProviderClient의 2분 이내 lastLocation을 사용합니다.
   없거나 오래되었으면 balanced accuracy의 getCurrentLocation을 요청합니다. 전체 대기는 15초로 제한합니다.
4. 위치 확인에 성공하면 NAVER 지도 앱의 `nmap://search?query=…&appname=…`을 우선 실행합니다.
5. 앱이 없거나 실행이 차단되면 `https://map.naver.com/p/search/…`를 처리하는 브라우저/외부 앱으로 연결합니다.
   모두 실행 불가하면 메시지를 표시하며 기존 Home 기능은 계속 사용할 수 있습니다.

**좌표로 검색 중심이 자동 설정되지는 않습니다.** 공식 `/search` 문서에는 좌표 파라미터가 없습니다.
임의의 lat/lng 파라미터를 만들거나 지도 앱을 두 번 실행하는 대신, “헬스장” 검색을 열고
**NAVER 지도 안에서 현재 위치 버튼을 누르도록** 카드와 메시지로 안내합니다.
NAVER 지도/브라우저가 자신의 위치 권한을 별도로 요청할 수 있습니다.

- [NAVER 공식 앱 딥링크 문서](https://guide.ncloud-docs.com/docs/application-maps-url-scheme-vpc)
- [NAVER 웨일 담당자가 안내한 웹 검색 URL](https://forum.whale.naver.com/topic/57381/)
- [Google FusedLocationProviderClient 문서](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)

## 비용·보안·개인정보

- NAVER Search API/API HUB/Maps SDK/Dynamic Map API, Edge Function, DB, API 키, 결제 설정은 추가하지 않습니다.
- `play-services-location:21.3.0`만 추가했습니다. 기존 Kotlin 2.1/JVM 17/minSdk 26 환경에서 빌드 검증합니다.
  지도 API가 아닌 기기의 일회성 위치 확인용이며 다른 Google 서비스 상품을 가입하지 않습니다.
- 이 기능이 개발자에게 호출하는 과금 대상 외부 API는 없습니다. 휴대폰의 일반 데이터 통신료는 별개입니다.
  기존 Supabase/YouTube 등 다른 기능의 서비스 요금까지 무료로 보장한다는 의미는 아닙니다.
- FINE/COARSE만 추가하고 BACKGROUND_LOCATION, 위치 서비스/Worker/알람을 추가하지 않습니다.
- 위치 객체는 helper 안에서 일시적으로 확인하고 외부에는 READY/실패 상태만 반환합니다.
  Room/Supabase/Preferences/DataStore/Intent/로그/분석 이벤트/저장 상태에 좌표를 넣지 않습니다.
- 좌표를 AIRPTCoach 서버나 NAVER 링크에 전달하지 않습니다. OS/Google 위치 제공자의 내부 처리는
  해당 서비스 정책을 따르고, 외부 지도의 위치 처리는 사용자가 외부 앱에서 허용한 권한을 따릅니다.
- 네이티브 딥링크는 NAVER 지도 패키지를 명시합니다. 웹은 고정된 HTTPS NAVER 주소만 사용합니다.
- `startActivity`의 ActivityNotFoundException과 SecurityException을 처리합니다.
- 중복 클릭을 막고 Home이 백그라운드로 이동하면 위치 요청을 취소합니다. 재진입 시 자동 재요청하지 않습니다.
  위치 권한 팝업 중 화면이 재생성되면 자동 검색 없이 버튼을 다시 눌러 사용할 수 있습니다.
- GPS OFF, 위치 null/실패/timeout, 권한 거부, Google Play 서비스 미지원은 안내만 표시합니다.
  인터넷이 없으면 외부 지도가 데이터를 표시하지 못할 수 있지만 AIRPTCoach의 다른 기능을 막지는 않습니다.

## 파일 책임

| 파일 | 역할 |
|---|---|
| `ui/gym/NearbyGymController.kt` | 버튼·Activity Result·로딩·lifecycle 연결 |
| `ui/gym/NearbyGymLocationHelper.kt` | 권한/위치 서비스 확인 및 한 번의 위치 요청 |
| `ui/gym/NearbyGymPolicy.kt` | 위치 유효성·중복 요청·권한 상태의 순수 정책 |
| `ui/gym/NearbyGymNavigation.kt` | 순수 URL 생성 및 앱→웹 fallback 정책 |
| `ui/gym/NearbyGymNavigator.kt` | 안전한 Android Intent 실행 |
| `values/strings_nearby_gym.xml` | 한국어 문구 |
| `ui/gym/NearbyGymTest.kt` (test) | 정책 및 URL/fallback JVM 테스트 |

기존 파일 변경은 `HomeActivity.kt`, `activity_home.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`, `README.md`입니다.
운동 분석·로그인·Room·Supabase·YouTube 코드와 서버 설정은 변경하지 않습니다.

## 검증

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Windows에서 IDE가 생성 파일을 잠근 경우 기존 프로젝트 설정의 `-PverificationBuildDir=nearby-map-verification`으로
검증 출력 디렉터리만 분리할 수 있습니다. 이때 APK는 `build/nearby-map-verification/outputs/apk/debug/app-debug.apk`입니다.

JVM 테스트는 대략적/정확한 권한, 권한 대기·거부, 중복 클릭, stop/재생성 정책, 위치 유효성·시각,
공식 URI 파라미터, URL 인코딩, 앱 우선 실행, 웹 fallback, 모든 연결 실패를 검증합니다.
실제 GPS·Google Play 서비스·권한 팝업·NAVER 앱/웹 화면 동작은 아래 실기기 확인이 필요합니다.

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

### 이번 검증 결과 (2026-10-03)

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
