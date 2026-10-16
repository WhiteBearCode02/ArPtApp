# EXP02 actual-model Golden: Windows 로컬 Python 환경

## 범위와 증거 경계

이 환경은 사용자가 명시적으로 승인한 **로컬 검증 전용** 환경이다. Android 의존성이나
production activation gate를 변경하지 않는다. TensorFlow 전체 패키지, CUDA, 서버,
유료 API, 모델 재학습/재export는 필요하지 않다.

- export 노트북 기록: Python 3.13.15, Ultralytics 8.4.176, ai-edge-litert 2.2.0.
- 현재 Windows 검증: 설치되어 있던 64-bit Python 3.11.9로 **새로운 격리 venv** 생성.
- LiteRT와 Ultralytics 버전은 export 기록과 동일하지만 Python/운영체제까지 동일한 환경은 아니다.
- 공식 API: `from ai_edge_litert.interpreter import Interpreter`.
- 공식 Windows wheel: `ai_edge_litert-2.2.0-cp311-cp311-win_amd64.whl`,
  SHA256 `c36ccb82a89f10f9e6c947f67f06dd00699caf5fe3bcb2be0eddcb213f70220d`.

출처: [LiteRT Python migration/API](https://developers.google.com/edge/litert/migration#migrate_with_python),
[공식 PyPI 2.2.0 배포](https://pypi.org/project/ai-edge-litert/2.2.0/).

## 설치 순서

프로젝트 루트 PowerShell 기준이다. 기존 디렉터리를 덮어쓰거나 system-site-packages를 사용하지 않는다.
설치 전 executable/version/pip/패키지 계획을 출력하고, 다운로드가 허용된 경우에만 설치한다.
`--only-binary`로 임의 source build를 피하고 공식 단일 index를 사용한다.

```powershell
$goldenBasePython = 'C:\Users\dlgus\AppData\Local\Python\pythoncore-3.11-64\python.exe'
& $goldenBasePython --version
& $goldenBasePython -m pip --version
git check-ignore --no-index -- local_validation/.venv/pyvenv.cfg
if (Test-Path -LiteralPath local_validation/.venv) { throw '기존 venv를 먼저 확인하세요.' }
& $goldenBasePython -m venv local_validation/.venv
& 'local_validation/.venv/Scripts/python.exe' -m pip --version
```

다음은 실제 확인한 최소 실행 환경의 고정 버전이다. wheel 다운로드만 네트워크를 사용한다.
사진/모델/생성된 tensor는 다운로드 명령에 전달하지 않는다.

```powershell
& 'local_validation/.venv/Scripts/python.exe' -m pip --isolated --disable-pip-version-check install `
  --only-binary=:all: --index-url https://pypi.org/simple --timeout 15 --retries 1 `
  ai-edge-litert==2.2.0 numpy==2.4.6 opencv-python-headless==4.13.0.92 `
  backports.strenum==1.2.8 flatbuffers==25.12.19 tqdm==4.70.1 typing-extensions==4.16.0 `
  protobuf==7.36.2 ml_dtypes==0.6.0 colorama==0.4.6
if ($LASTEXITCODE -ne 0) { throw 'LiteRT 환경 설치 실패: inference를 실행하지 마세요.' }

& 'local_validation/.venv/Scripts/python.exe' -m pip --isolated --disable-pip-version-check install `
  --only-binary=:all: --index-url https://download.pytorch.org/whl/cpu --timeout 15 --retries 1 `
  torch==2.9.1+cpu filelock==3.32.3 sympy==1.14.0 networkx==3.6.1 jinja2==3.1.6 `
  fsspec==2026.7.0 mpmath==1.3.0 MarkupSafe==3.0.3
if ($LASTEXITCODE -ne 0) { throw 'CPU Torch 환경 설치 실패' }

# 저장소 도구가 SHA256을 검사하는 기존 wheel만 설치한다. 전체 YOLO 환경 설치가 아니다.
& 'local_validation/.venv/Scripts/python.exe' -m pip --isolated --disable-pip-version-check install `
  --only-binary=:all: --no-deps build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl
if ($LASTEXITCODE -ne 0) { throw '고정 Ultralytics wheel 설치 실패' }
```

Ultralytics는 `--no-deps`로 설치한 **선택 함수 검증용 배포본**이다. `import ultralytics`,
`YOLO(...)`, 전체 `.val()`을 실행하는 범용 환경이 아니다. 전체 배포본의 미설치
matplotlib/torchvision/GUI OpenCV 등의 metadata 요구 때문에 `pip check` 또는 추가 설치 시
의존성 경고가 발생할 수 있다. 이를 전체 패키지 호환성 PASS라고 보고하지 않는다.
단순히 경고를 없애기 위한 추가 설치도 하지 않는다.

reference 도구는 wheel SHA256/추출 원본 바이트를 확인한 뒤 AST로 선택한
preprocessing/forward/NMS 함수만 실행한다. 전체 YOLO 초기화, 자동 설치,
weights 다운로드, telemetry 경로를 실행하지 않는다. 깨끗한 별도 프로세스에서 실행하여
`torchvision`의 사전 import에 따른 NMS 구현 변경을 방지한다.

## import와 실제 모델 실행 확인

```powershell
& 'local_validation/.venv/Scripts/python.exe' -c "import sys,importlib.metadata as m,numpy,cv2,torch; from ai_edge_litert.interpreter import Interpreter; print(sys.executable); print({p:m.version(p) for p in ['ai-edge-litert','ultralytics','numpy','opencv-python-headless','torch']}); print('torchvision preloaded:', 'torchvision' in sys.modules)"
```

실제 Interpreter 생성/모델 로드/할당 후 읽은 tensor가 FLOAT32 `[1,3,640,640]` /
`[1,6,8400]`인지 확인한다. `inspect-exp02-model.py`의 정적 FlatBuffer 검사만으로
native 실행 성공을 주장하면 안 된다. actual generator는 크기/hash/metadata 검증 후
실제 Interpreter invoke를 수행하고, 네 입력 모두 성공한 뒤에만 reference JSON을 발행한다.

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:YOLO_AUTOINSTALL = 'false'
$env:YOLO_OFFLINE = 'true'
$env:OMP_NUM_THREADS = '1'
& 'local_validation/.venv/Scripts/python.exe' -u scripts/generate-exp02-actual-golden.py `
  --manifest local_validation/exp02_golden/manifest.json `
  --model app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite `
  --output local_validation/exp02_golden `
  --reference-filename reference_results.json
```

## 현재 실행 상태 (2026-10-17)

- 새 venv의 LiteRT/NumPy/OpenCV/CPU Torch import 성공. Ultralytics 배포 metadata 버전 확인.
- 실제 EXP02 Interpreter 생성, 모델 load, tensor allocation/shape/dtype 확인 성공.
- 사용자 제공 네 이미지에 Python actual-model inference 실행 성공. private JSON/RGB/FLOAT32 fixture 생성.
- 오분류와 무검출은 예상 라벨로 삭제하거나 수정하지 않는다. 실제 결과는 private reference에만 보관한다.
- 사용자 기기에 모델과 별도 동의받은 Golden 파생 자료를 private 주입했다. Android Interpreter와
  metadata 확인 후 native inference4회 성공. 네 입력이 모두 무검출로 일치하여 **negative-path
  equivalence PASS**, positive bbox/confidence/NMS는 미검증이다.
- Python reference 실행 성공만으로 Golden equivalence PASS 또는 최종 VERIFIED로 승격하지 않는다.
- 모든 입력이 무검출이면 Android와 빈 목록이 같아도 positive class/confidence/bbox/NMS의
  실증 범위는 부족하다. 해당 제한을 숨기지 않고, threshold 튜닝 없이 추가 양성 검증 자료를 별도 검토한다.

`local_validation/exp02_golden/reference_results.json`과 `VALIDATION_STATUS.md`는 Git 제외다.
venv/원본/생성 RGB/생성 tensor/model을 assets에 복사하거나 Git에 추가하지 않는다.
모델 원본이 PC의 assets 폴더에 기존 보관되어 있어도 APK exclusion 정책은 그대로 유지한다.

Git 검사 범위를 구분한다. 이번 EXP02 모델과 Golden/venv/fixture는 모두 ignored·untracked다.
기존 `app/src/main/assets/yolo11n-pose_int8.tflite`는 이미 tracked된 별도 자산이다.
이번 작업에서 삭제하거나 untrack하지 않았으며, 현재 debug/release APK에서는 다른 loose
YOLO weights와 함께 제외된 것을 확인했다. 기존 모델의 공개 권리까지 확인했다는 뜻은 아니다.

전용 Python 환경 구성 단계에서 Python guard 테스트 26개, 정적 metadata 테스트 13개, `scripts/*.test.mjs`
74개가 통과했다. 기존 debug/release/test APK ZIP의 private fixture/weight entry는 0이고
debug/release의 필수 MediaPipe task는 유지된다. 원본 이미지 4개의 SHA256은 작업 전과 같다.
변경 공개 파일 13개에 대한 scoped secret-pattern 검사와 `git diff --check`를 통과했다.
이전 환경/문서 단계에서는 Gradle을 재실행하지 않았다. 이후2026-10-17 provisioning 도구 수정
후 assembleDebug/testDebugUnitTest --offline가 성공했다(58초,49tasks up-to-date). 이는 JVM 테스트
새 실행과 구분한다. 별도 실제 휴대폰 instrumentation1개가 통과했고 native inference4회를 수행했다.

## Android 재현 절차와 남은 검증

1. USB debugging을 켠 본인 휴대폰을 연결하고 `adb devices -l`에서 승인 상태/serial을 확인한다.
2. 빌드된 DEBUG 앱과 instrumentation APK를 설치한다. release provisioning은 허용하지 않는다.
3. [실기기 체크리스트](EXP02_ANDROID_DEVICE_SHADOW_CHECKLIST.md)의 helper로 모델과 Golden bundle을
   private `noBackupFilesDir/yolo/` 경로에 주입한다. 모델을 APK assets에 추가하지 않는다.
4. `Exp02ActualModelGoldenTest`를 실행하여 실제 Android load/invoke 및 전처리/최종 detection 차이를 기록한다.
5. private `android-result.json`의 실패/제한을 검토한 뒤에만 별도 코드 리뷰로 승격 여부를 판단한다.
   현재 앱 gate는 닫혀 있으므로 모델 주입만으로 실시간 Shadow prediction이 활성화되지 않는다.

위1~4는 현재 기기에서 실행 완료했다. Dashboard도 MODEL_MISSING이 아닌
CONTRACT_NOT_VERIFIED / prediction 비활성으로 실제 확인했다. 다음 검증은 positive detection을
포함한 자료에서 bbox/confidence/NMS를 확인하는 단계다. threshold나 gate를 임의로 바꾸지 않는다.

검증/설치는 로컬 CPU에서만 수행한다. 배포·원격 DB·Edge Function·billing·LLM 연결은 변경하지 않았다.
