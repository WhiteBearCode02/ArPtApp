import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const provision = read('scripts/provision-exp02-model.ps1');
const generator = read('scripts/generate-exp02-actual-golden.py');
const instrumented = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02ActualModelGoldenTest.kt');

test('private DEBUG provisioning is binary-safe, validated, and atomic', () => {
    assert.match(provision, /\$binaryStdin = \$process\.StandardInput\.BaseStream/);
    assert.match(provision, /CopyToAsync\(\$binaryStdin\)/);
    assert.match(provision, /\[void\]\$copy\.GetAwaiter\(\)\.GetResult\(\)/);
    assert.match(provision, /\$binaryStdin\.Flush\(\)/);
    assert.match(provision, /\$binaryStdin\.Close\(\)/);
    assert.doesNotMatch(provision, /\$process\.StandardInput\.Close\(/);
    assert.match(provision, /'exec-in', 'run-as', \$PackageName, 'dd'/);
    assert.match(provision, /'iflag=count_bytes', "count=\$expectedBytes"/);
    assert.match(provision, /'conv=fsync', 'status=none'/);
    assert.doesNotMatch(provision, /@\('shell', '-T'/);
    assert.match(provision, /Get-PrivateFileSha256/);
    assert.match(provision, /hasher\.ComputeHash\(\$hashStream\)/);
    assert.match(provision, /'sha256sum', \$temporary/);
    assert.match(provision, /'mv', \$temporary, \$upload\.Target/);
    assert.match(provision, /no_backup\/yolo/);
    assert.doesNotMatch(provision.replace(/^\s*#.*$/gm, ''), /adb push|\/sdcard|ConvertTo-SecureString|Set-Content/);
});

test('private provisioning stops the validated DEBUG package only after confirmation', () => {
    assert.ok(provision.indexOf('ShouldProcess("DEBUG') < provision.indexOf("'am', 'force-stop'"));
    assert.match(provision, /'chmod', '600', \$temporary/);
    assert.match(provision, /120000 - \[int\]\$deadline\.ElapsedMilliseconds/g);
    assert.match(provision, /CommandType -ne 'Application'/);
});

test('provisioning resolves its default model path after parameter binding and retains mismatch guards', () => {
    const parameters = provision.slice(0, provision.indexOf('# DEBUG-only'));
    assert.doesNotMatch(parameters, /\$PSScriptRoot/);
    assert.match(provision, /\$ModelPath = Join-Path \$PSScriptRoot/);
    assert.match(provision, /MODEL_HASH_MISMATCH/);
    assert.match(provision, /expectedBytes=\$expectedBytes actualBytes=\$actualBytes/);
    assert.ok(provision.indexOf('MODEL_HASH_MISMATCH') < provision.indexOf("'mv', $temporary, $upload.Target"));
});

test('actual reference invokes real interpreter with pinned original source loader, not synthetic output', () => {
    assert.match(generator, /from ai_edge_litert\.interpreter import Interpreter/);
    assert.match(generator, /Interpreter\(model_path=str\(model\), num_threads=2\)/);
    assert.match(generator, /loader\.load_reference\(args\.source_root, args\.wheel\)/);
    assert.match(generator, /reference\["forward"\]\(backend, tensor\)/);
    assert.match(generator, /modelInferenceExecuted=True, androidNativeInferenceExecuted=False/);
    assert.doesNotMatch(generator, /SyntheticInterpreter\(|pip install|urlopen|requests\.|shell\s*=\s*True/);
    assert.match(generator, /subprocess\.run\(\["git"/);
    assert.match(generator, /"check-ignore"/);
    assert.match(generator, /"ls-files"/);
});

test('actual media remains private and all four exercise/view inputs are required, not positive predictions', () => {
    assert.match(generator, /build in destination\.parents/);
    assert.match(generator, /local_validation/);
    assert.match(generator, /Symlink\/junction Golden output paths are forbidden/);
    assert.match(generator, /tracked\.stdout/);
    assert.match(generator, /rightsCleared/);
    assert.match(generator, /covered != REQUIRED/);
    assert.doesNotMatch(generator, /not detections or not any|positive correctness Golden/);
    assert.match(generator, /PROVISIONAL_NOT_MEASURED/);
    assert.match(generator, /detectionCount=0, classMatch="EXACT"/);
});

test('semantic correctness is recorded separately without rejecting empty or wrong-class detections', () => {
    assert.match(generator, /semanticMatch=top is not None/);
    assert.match(generator, /hasExpectedDetection=any/);
    assert.match(generator, /top = detections\[0\] if detections else None/);
    assert.match(generator, /classOrdering=\[detection\["classId"\]/);
    assert.match(generator, /not dataset accuracy and not an equivalence gate/);
});

test('actual private tensor fixture records runtime provenance and separately measurable preprocessing', () => {
    assert.match(generator, /inputTensorFile=tensor_name/);
    assert.match(generator, /inputTensorByteOrder="LITTLE_ENDIAN"/);
    assert.match(generator, /dtype=np\.dtype\("<f4"\)/);
    assert.match(generator, /numpyVersion=np\.__version__/);
    assert.match(generator, /opencvVersion=cv2\.__version__/);
    assert.match(generator, /torchVersion=torch\.__version__/);
    assert.match(generator, /--reference-filename/);
    assert.match(generator, /reference_filename \+ "\.pending"/);
    assert.match(generator, /not np\.isfinite\(raw\)\.all\(\)/);
    assert.match(generator, /post_nms_detections\(processed, width, height\)/);
});

test('instrumentation compares identical raster hashes and direct native results without verifying production gate', () => {
    assert.match(instrumented, /assertTrue\("Actual-model harness is DEBUG-only", BuildConfig.DEBUG\)/);
    assert.match(instrumented, /DebugYoloModelSource\.open\(context\)/);
    assert.match(instrumented, /runtime\.run\(prepared\.tensor, output\)/);
    assert.match(instrumented, /YoloActualGoldenComparator\.compare\(expected, actual\)/);
    assert.match(instrumented, /sample\.rgbSha256/);
    assert.doesNotMatch(instrumented, /ExportContractVerification\.VERIFIED|setPreferences|putBoolean|assumeTrue|@Ignore/);
});

test('example is not actual-model Golden evidence and does not grant rights by default', () => {
    const example = JSON.parse(read('docs/ml/exp02-actual-golden.manifest.example.json'));
    assert.equal(example.samples.length, 4);
    assert.ok(example.samples.every(sample => sample.rightsCleared === false));
    assert.ok(example.samples.every(sample => !('detections' in sample)));
});
