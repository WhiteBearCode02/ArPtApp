# 공개 연구 프로젝트 라이선스·보안 검토

검토일: 2026-10-13. 졸업작품·졸업논문·취업 포트폴리오의 비상업적 공개 프로젝트를 전제로 합니다. 법률 자문/권리 보증이 아니며 유료 계약·자동 commit/push·Git 이력 재작성은 하지 않습니다.

## 저장소 상태와 중단 조건

루트/추적 파일에서 기존 LICENSE/COPYING을 찾지 못했습니다. 사용자 지침에 따라 AGPL-3.0 공개 운영을 목표로 하지만, 다음 관계를 확인하기 전 **정식 LICENSE를 자동 생성하거나 전체 코드의 라이선스를 확정하지 않습니다.** GNU의 [AGPL-3.0 원문](https://www.gnu.org/licenses/agpl-3.0.html)을 사용하는 적용안은 아래 확인 후 제안합니다.

1. 기존 `com.google.android.gms:play-services-location:21.3.0`의 Android SDK License 관계를 검토 대상으로 기록했습니다. 2026-10-13 사용자 승인으로 SDK 참조를 제거하고 Android 기본 LocationManager로 대체했습니다. SDK를 재라이선스하지 않았고 새 위치 dependency를 추가하지 않았습니다.
2. EXP02 학습 출처는 사용자가 AI-Hub라고 확인했으나 정확한 데이터셋명/ID/URL과 weight/APK 재배포 조건은 미확정입니다. 모델 파일은 로컬 보존/Git 제외 상태입니다.
3. 기존 TFLite/MediaPipe 모델 자산의 출처·고지 증빙도 공개 전에 확인해야 합니다. 기존 기능 보존을 위해 임의 삭제하지 않습니다.

## 가능한 다음 경로 — 사용자 승인 필요

- Google Play services 위치 의존성 교체는 사용자 승인 후 수행했습니다. foreground 권한·위치 실패 fallback·중복 클릭 방지·외부 지도 UI를 보존합니다. 대체만으로 전체 라이선스 문제가 해결된다고 보증하지 않습니다.
- 실제 학습 데이터 출처/라이선스/사용 범위/필수 고지를 제공받고 weight 재배포 여부를 별도로 결정합니다. '로컬 연구용 통합만 허용, 모델/APK 배포 금지' 방식은 사용자가 그 범위를 명시적으로 승인한 뒤 검토합니다.

## 모델의 읽기 전용 검사

실제 모델: 입력 FLOAT32 `[1,3,640,640]`, 출력 FLOAT32 `[1,6,8400]`, 9,894,030 bytes. 추정이 아니라 실제 FlatBuffer에서 확인했습니다. Interpreter 실행 및 parser/카메라 구현은 아직 하지 않았습니다. 상세 provenance/제한은 [MODEL_CARD.md](../MODEL_CARD.md)에 기록했습니다.

파일 크기는 GitHub의 일반 Git 단일 파일 차단 기준 100 MiB보다 작지만, 크기 통과가 공개 허가를 의미하지 않습니다. [GitHub 공식 파일 정책](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)

## 비밀정보와 데이터

- 서명키·환경설정·로그·덤프·체크포인트·원본 영상/이미지/압축파일 및 dataset 폴더를 Git에서 제외합니다.
- 검토한 빈 metadata 템플릿만 명시적으로 허용합니다. ignore는 이미 추적된 파일의 수정/이력까지 숨기지 않으므로 기존 metadata를 채울 때도 diff 검토가 필요합니다.
- Supabase의 publishable/legacy anon은 공개 클라이언트용이며 secret/service_role은 서버 전용입니다. 현재 정책은 실제 값은 Git에 넣지 않고 local.properties에서 공급하는 것입니다. 공개 키라도 RLS·사용자 인증·권한 설정을 대체하지 않습니다. [공식 키 안내](https://supabase.com/docs/guides/getting-started/api-keys)
- Android BuildConfig/모델 metadata에 secret/private 키를 넣으면 .gitignore와 관계없이 APK에서 노출될 수 있습니다. 원격 DB/RLS 상태는 이번 로컬 검사로 검증하지 않았습니다.

## 검사 범위와 한계

로컬 Git 전체 refs 162개 commit의 변경 내용에서 주요 비밀키 패턴, 그리고 local.properties/서명키/환경 파일 경로 이력 검색을 수행해 발견 사항이 없었습니다. 검사 패턴은 private key header, Supabase secret, OpenAI secret, Google API key, JWT 형태입니다. 일반 문자열 암호·모든 토큰 형식·PII·바이너리 모델 metadata 전체·원격의 삭제된 refs까지 포괄하는 보안 감사는 아닙니다.

검사 중 비밀정보 값은 출력하지 않았으며 키 회전·history cleanup을 수행하지 않았습니다. 향후 과거 노출을 확인하면 공개 저장소 파일 삭제만으로 끝내지 않고 키 회전 및 사용자 승인 후 이력 정리 여부를 보고합니다.

초기 점검 이후 사용자 승인 범위의 Android 위치 어댑터/제공자 정책/테스트와 Gradle 의존성 제거를 추가했습니다. YOLO 추론에는 아직 연결하지 않았습니다. 로컬 assets 모델은 빌드 APK에 포함될 수 있으므로 로컬 검증 APK를 공개 배포 가능한 파일로 간주하지 않습니다. 새로운 영상 저장·landmark 서버 전송·위치 서버 전송·유료 API·라이브러리는 추가하지 않았습니다.
