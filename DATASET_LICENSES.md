# 데이터 출처·라이선스 검토

이 문서는 원본 데이터를 제공하지 않는 출처 기록입니다. 원본 영상·이미지·전체 프레임·타사 데이터·압축파일은 저장소에 포함하거나 재배포하지 않습니다. 사용자는 AI-Hub 데이터를 사용했다고 확인했지만, 개별 데이터셋 목록과 권리 조건은 아직 확인되지 않았습니다.

## AI-Hub — 사용자 확인 학습 출처

| 항목 | 현재 상태 |
|---|---|
| 제공처 | AI-Hub, 사용자 설명으로 학습 사용 확인 |
| 정확한 데이터셋명 / ID / 원본 URL | 미제공. 포털명만으로 특정 데이터의 이용 조건을 확정하지 않음 |
| 라이선스 / 모델 재배포 / 필수 attribution | 해당 데이터셋 이용 조건 확인 필요 |
| 사용 범위 / split / negative 출처 | 세부 목록 미제공 |
| 저장소 포함 | 원본 데이터 및 모델 weight 공개 제외 유지 |

정확한 데이터셋 페이지와 이용 조건을 받은 뒤 출처·사용 범위·고지를 채웁니다. 학술 목적이라는 이유만으로 모델/APK 재배포 가능성을 확정하지 않습니다.

## RickyRiccio/Real_Time_Exercise_Recognition_Dataset

이 항목은 이전 검토 후보 기록이며, 사용자에게 실제 학습 사용이 확인된 AI-Hub 출처와 구분합니다. EXP02 사용 데이터라고 주장하지 않습니다.

| 항목 | 확인 내용 |
|---|---|
| 제공처 | Hugging Face, RickyRiccio |
| 원본 URL | https://huggingface.co/datasets/RickyRiccio/Real_Time_Exercise_Recognition_Dataset |
| 제공자 표시 라이선스 | CC BY-NC-SA 4.0 |
| 앱 모델의 실제 사용 범위 | 미확인. 사용 여부, 파일/클래스별 범위와 split을 사용자에게 확인해야 함 |
| 저장소 포함 | 원본 미포함, 다운로드/재배포하지 않음 |
| Attribution | 실제 사용 시 제공자·데이터셋명·원본 링크·라이선스 링크·변경 사항 및 제공된 기타 고지를 기록 |

데이터셋 카드의 [라이선스 표기와 설명](https://huggingface.co/datasets/RickyRiccio/Real_Time_Exercise_Recognition_Dataset)을 확인했습니다. [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)은 저작자 표시·비영리·동일조건변경허락 조건을 포함합니다. 학술 목적이라는 이유만으로 출처·권리 검토를 생략하지 않습니다.

제공자 카드에는 Kaggle Workout/Exercises 영상, InfiniteRep 합성 영상, Pexels/Pixabay/Shutterstock 등을 포함한 온라인 영상이 언급됩니다. 모든 개별 원본의 권리가 카드 표기 하나로 확정되었다고 판단하지 않습니다. 실제 사용한 원본별 제공처·URL·조건·동의 증빙을 별도로 확인해야 합니다.

**학습된 weight 재배포에 이 조건이 어떻게 적용되는지는 추가 검토가 필요합니다 (trained-weight redistribution implications may require additional review).** AGPL 소스 공개와 데이터셋의 비영리/동일조건 조건이 자동으로 동일하거나 호환된다고 결론 내리지 않습니다.

## 기타 데이터 / 개인 촬영 / background negative

현재 출처 목록·개별 URL·라이선스·촬영 및 초상/개인정보 동의·사용량은 미확인입니다. EXP02 모델 카드의 positive/negative 설명은 사용자 제공 정보이며 권리 확인 증빙이 아닙니다. 아래 항목을 사용 데이터마다 채워야 합니다.

| 데이터셋/출처 이름 | 원본 제공처/URL | 라이선스·사용 허가 | 사용 클래스·범위 | attribution·변경 | weight/APK 배포 검토 |
|---|---|---|---|---|---|
| 미제공 | 확인 필요 | 확인 필요 | 확인 필요 | 확인 필요 | 보류 |

원본이 직접 촬영된 자료라도 사람의 얼굴·신체 영상 및 개인 식별정보를 자동으로 공개하지 않습니다. 공개용 manifest에는 개인 계정, 다운로드 토큰, 서명 URL, 로컬 절대 경로를 넣지 않습니다.

## 현재 저장소 메타데이터

`ai_module/dataset/metadata/dataset_manifest.json`은 samples 0의 구조 템플릿이고 `local_sources.json`, `youtube_sources.json`은 빈 sources 템플릿입니다. 이것이 외부에서 학습한 EXP02 데이터가 없었다는 증거는 아닙니다. 검토한 템플릿만 예외적으로 추적하며 신규 생성 metadata/manifest도 별도 공개 검토 후 포함해야 합니다.
