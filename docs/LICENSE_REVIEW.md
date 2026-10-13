# 공개 연구 프로젝트 라이선스·보안 검토

검토일: 2026-10-13. 졸업작품·졸업논문·취업 포트폴리오의 비상업적 공개 프로젝트를 전제로 합니다. 법률 자문/권리 보증이 아니며 유료 계약·자동 commit/push·Git 이력 재작성은 하지 않습니다.

## 저장소 상태와 중단 조건

루트/추적 파일에서 기존 LICENSE/COPYING을 찾지 못했습니다. 사용자 지침에 따라 AGPL-3.0 공개 운영을 목표로 하지만, 다음 관계를 확인하기 전 **정식 LICENSE를 자동 생성하거나 전체 코드의 라이선스를 확정하지 않습니다.** GNU의 [AGPL-3.0 원문](https://www.gnu.org/licenses/agpl-3.0.html)을 사용하는 적용안은 아래 확인 후 제안합니다.

1. 기존 `com.google.android.gms:play-services-location:21.3.0`의 Android SDK License 관계를 검토 대상으로 기록했습니다. 2026-10-13 사용자 승인으로 SDK 참조를 제거하고 Android 기본 LocationManager로 대체했습니다. SDK를 재라이선스하지 않았고 새 위치 dependency를 추가하지 않았습니다.
2. 실제 YOLO EXP01/EXP02는 Dataset A(RickyRiccio/Real_Time_Exercise_Recognition_Dataset의 similar_dataset) 기반입니다. 제공자 라이선스는 CC BY-NC-SA 4.0이며 trained-weight 재배포·개별 원본 권리·attribution은 추가 검토가 필요합니다. Dataset B AI-Hub 크로스핏 동작 데이터는 pose/form/biomechanics용으로 detector 학습에 사용하지 않았습니다.
3. 기존 YOLO11 pose 자산은 현재 실행 경로 호출 미확인의 unused asset candidate이며 source/license 증빙이 필요합니다. MediaPipe lite task는 공식 자산과 크기/MD5 일치 및 공식 모델 카드의 Apache-2.0 표기를 확인했지만 bundle 구성요소 고지는 별도로 확인합니다. 두 파일 모두 삭제하지 않습니다.

## 가능한 다음 경로 — 사용자 승인 필요

- Google Play services 위치 의존성 교체는 사용자 승인 후 수행했습니다. foreground 권한·위치 실패 fallback·중복 클릭 방지·외부 지도 UI를 보존합니다. 대체만으로 전체 라이선스 문제가 해결된다고 보증하지 않습니다.
- Dataset A 원본별 권리·필수 고지 및 weight 재배포 조건을 검토하고 모델 공개 여부를 별도로 결정합니다. Dataset B의 정확한 AI-Hub ID/URL/이용조건은 pose/form 데이터 공개 검토에 필요하며 detector 출처와 혼동하지 않습니다.
- 이번 작업은 문서 정리뿐이며 Android 통합/새 모델 포함 APK를 생성하지 않습니다. 로컬 연구용 통합도 사용자 승인 범위와 export/inference 검증을 확인한 뒤 별도 작업으로 진행합니다.

## 모델의 읽기 전용 검사

실제 모델: 입력 FLOAT32 `[1,3,640,640]`, 출력 FLOAT32 `[1,6,8400]`, 9,894,030 bytes. 추정이 아니라 실제 FlatBuffer에서 확인했습니다. Interpreter 실행 및 parser/카메라 구현은 아직 하지 않았습니다. 상세 provenance/제한은 [MODEL_CARD.md](../MODEL_CARD.md)에 기록했습니다.

파일 크기는 GitHub의 일반 Git 단일 파일 차단 기준 100 MiB보다 작지만, 크기 통과가 공개 허가를 의미하지 않습니다. [GitHub 공식 파일 정책](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)

## 비밀정보와 데이터

- 서명키·환경설정·로그·덤프·체크포인트·원본 영상/이미지/압축파일 및 dataset 폴더를 Git에서 제외합니다.
- 검토한 빈 metadata 템플릿만 명시적으로 허용합니다. ignore는 이미 추적된 파일의 수정/이력까지 숨기지 않으므로 기존 metadata를 채울 때도 diff 검토가 필요합니다.
- Supabase의 publishable/legacy anon은 공개 클라이언트용이며 secret/service_role은 서버 전용입니다. 현재 정책은 실제 값은 Git에 넣지 않고 local.properties에서 공급하는 것입니다. 공개 키라도 RLS·사용자 인증·권한 설정을 대체하지 않습니다. [공식 키 안내](https://supabase.com/docs/guides/getting-started/api-keys)
- Android BuildConfig/모델 metadata에 secret/private 키를 넣으면 .gitignore와 관계없이 APK에서 노출될 수 있습니다. 원격 DB/RLS 상태는 이번 로컬 검사로 검증하지 않았습니다.

## 검사 범위와 한계

이전 보안 점검에서 당시 로컬 Git 전체 refs 162개 commit의 변경 내용에서 주요 비밀키 패턴, 그리고 local.properties/서명키/환경 파일 경로 이력 검색을 수행해 발견 사항이 없었습니다. 검사 패턴은 private key header, Supabase secret, OpenAI secret, Google API key, JWT 형태입니다. 일반 문자열 암호·모든 토큰 형식·PII·바이너리 모델 metadata 전체·원격의 삭제된 refs까지 포괄하는 보안 감사는 아닙니다.

검사 중 비밀정보 값은 출력하지 않았으며 키 회전·history cleanup을 수행하지 않았습니다. 향후 과거 노출을 확인하면 공개 저장소 파일 삭제만으로 끝내지 않고 키 회전 및 사용자 승인 후 이력 정리 여부를 보고합니다.

초기 점검 이후 사용자 승인 범위의 Android 위치 어댑터/제공자 정책/테스트와 Gradle 의존성 제거를 추가했습니다. YOLO 추론에는 아직 연결하지 않았습니다. 로컬 assets 모델은 빌드 APK에 포함될 수 있으므로 로컬 검증 APK를 공개 배포 가능한 파일로 간주하지 않습니다. 새로운 영상 저장·landmark 서버 전송·위치 서버 전송·유료 API·라이브러리는 추가하지 않았습니다.

## 이번 실험 이력 정정과 LICENSE 판정

**판정: 추가 검토 필요.** 루트/추적 파일에 정식 repository LICENSE가 없음을 확인했고, 승인 전 생성/수정/stage/commit하지 않았습니다.

- Ultralytics AGPL 운영 방향은 유지하되 Dataset A의 CC BY-NC-SA 4.0이 trained weight의 Adapted Material/ShareAlike에 적용되는지 결론 내리지 않습니다. dataset card의 표시만으로 개별 외부 영상 권리까지 확정하지 않습니다.
- AndroidX/CameraX/Room/Material/MediaPipe/TensorFlow Lite/Ktor/Coroutines/Gson/MPAndroidChart의 기존 Apache-2.0 표기와 Supabase Kotlin의 MIT 표기는 [기존 고지](../THIRD_PARTY_NOTICES.md)를 유지합니다. 새 dependency 없음. 전체 전이/native 구성과 APK 고지 조건을 감사 완료했다고 주장하지 않습니다.
- Google Play services Location은 제거된 과거 의존성입니다. 다시 도입하거나 원본 license를 재라이선스하지 않습니다.
- formal LICENSE 적용 전 프로젝트 코드의 권리 보유 범위, 기존 자산·dataset·weight 조건, 필요한 원본 notices/attribution과 packaging 제외 설정을 검토해야 합니다.
- EXP01 baseline의 SQUAT GROUP_g02/g04/g06 train+val leakage, EXP02의 group-aware/negative 조건, PT/LiteRT 제공 test 결과와 한계를 [ML_EXPERIMENTS.md](ML_EXPERIMENTS.md)에 구분했습니다. 새 학습·평가·실험 manifest 검증은 수행하지 않았습니다.
- 이번에 기존 앱 기능/Gradle/.gitignore/모델을 바꾸지 않았고 APK 빌드·모델 stage·자동 commit/push·이력 재작성·전체 보안 이력 재검사는 하지 않았습니다.

### Android integration 진행 조건

문서 불일치는 정리했지만 **모델을 포함한 공개 APK/실시간 통합을 지금 승인된 상태로 간주하지 않습니다.** Dataset A weight 재배포 및 자산 고지/프로젝트 license 검토, 실제 export 설정·전처리/후처리·Interpreter 검증 계획을 확인하고 사용자 승인 후 진행합니다. shape만으로 RGB/BGR·normalization·bbox·class score·NMS 의미를 확정하지 않습니다.

현재 로컬 `assets`의 파일은 기본 빌드에 포함될 수 있습니다. ignore가 APK 제외라는 오해를 막기 위해 상태를 기록했으며, 이번에는 파일 이동/삭제나 Gradle 제외 설정 변경으로 앱에 손대지 않고 새 APK 생성 자체를 하지 않았습니다.
