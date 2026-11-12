# EXP04 실제 기기 Shadow 체크리스트

현재 **NOT_RUN**. 이 템플릿은 실행 결과가 아닙니다. actual Golden/Android native가 검토되고 별도 코드 변경으로 contract를 승격하기 전에는 실시간 EXP04 prediction이 차단됩니다.

## 선행 조건

- [ ] 본인 기기·촬영 자료 사용 허가, 다른 사람이 프레임에 없음
- [ ] 7종 실제 TEST 프레임의 출처/권리/subject split 확인; Git/APK/외부 업로드 없음
- [ ] PC reference 실제 inference 및 Android exact tensor/final detection 비교 기록
- [ ] exact 모델 load/metadata/Android native invoke 증거, 잘못된 모델 거부 확인
- [ ] FAIL 증거 보존, confidence/NMS/stabilizer threshold 조작 없음
- [ ] 별도 검토 후 승격. 코드 생성/합성 테스트/Colab PASS만으로 승격하지 않음
- [ ] 명시적 exp04Debug, 기존 앱/EXP02/03 증거·기록 보존, Landmark Primary / release OFF

## 운동 순서와 최소 관찰 시간

각 종목 **최소 10초**, 가능한 정면/측면을 구분합니다. 양쪽을 따로 하는 동작은 좌/우 조건도 따로 적습니다. 안전한 범위에서만 동작하며 통증/불편 시 중단합니다. 덤벨 사용 여부·가림·조명·카메라 거리·전후면·줌·촬영 방향을 기록하되 불필요한 개인정보/영상은 수집하지 않습니다.

| 순서 / class | 운동 | 실제 관찰 |
|---|---|---|
| 0 | SQUAT | NOT_RUN |
| 1 | SHOULDER_PRESS | NOT_RUN |
| 2 | BICEP_CURL | NOT_RUN; raw→stable 전이와 좌/우 별도 관찰 |
| 3 | PUSH_UP | NOT_RUN |
| 4 | BENT_OVER_ROW | NOT_RUN |
| 5 | TRICEPS_EXTENSION | NOT_RUN; 좌/우 별도 관찰 |
| 6 | SIDE_BEND | NOT_RUN; 좌/우 별도 관찰 |

## 종목·구도별 기록 양식

아래는 빈 양식입니다. 실제 출력이 없으면 0이나 PASS로 채우지 말고 NOT_MEASURED/NOT_RUN을 유지하세요. 결과는 local-only run에 보관합니다.

```text
run / app build / device OS (기기 serial·계정 정보 제외):
modelId / hash / model status / contract status:
expected exercise / FRONT,SIDE,DIAGONAL / left,right:
camera front,back / zoom / 조명·가림·거리:
관찰 시간(최소 10초) / Landmark Primary:
YOLO raw / raw confidence / stable / shadow status:
processed frames / UNKNOWN,HOLD count·ratio:
inference latency / total latency / processed FPS:
thermal start,max,end / 육안 발열 / 장시간 관찰 범위:
예상치 못한 class switching / 시간:
실제 반복 횟수 / app rep count (Landmark):
TTS 내용 / report 결과 / History 재열기:
판정 / 불일치 / 중단 사유 / 다음 재현 조건:
```

종료·저장 후 운동 기록→해당 리포트→YOLO Shadow 진단에서 모델 identity와 raw/stable 집계가 당시 종목 수대로 보이는지 확인합니다. 이 진단은 운동 결과의 근거가 아니라 Shadow 관찰 기록입니다. YOLO 결과로 회차·점수·TTS·sessionExerciseType이 바뀌면 회귀 오류입니다.

7종 제한 관찰을 전체 모델 accuracy·임상적 정확성·Production 준비 완료로 표현하지 않습니다. BICEP_CURL/SIDE_BEND는 별도 관찰 대상이지 사전 실패 판정이 아닙니다. 장시간/발열 미실행이면 명시적으로 남깁니다.
