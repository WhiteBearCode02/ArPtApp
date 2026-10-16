import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const provision = read('scripts/provision-exp02-model.ps1');
const generator = read('scripts/generate-exp02-actual-golden.py');
const instrumented = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02ActualModelGoldenTest.kt');

test('private DEBUG provisioning is binary-safe, validated, and atomic', () => {
    assert.match(provision, /CopyToAsync\(\$process\.StandardInput\.BaseStream\)/);
    assert.match(provision, /'exec-in', 'run-as', \$PackageName, 'dd'/);
    assert.match(provision, /Get-FileHash/);
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

test('actual reference invokes real interpreter with pinned original source loader, not synthetic output', () => {
    assert.match(generator, /from ai_edge_litert\.interpreter import Interpreter/);
    assert.match(generator, /Interpreter\(model_path=str\(model\), num_threads=2\)/);
    assert.match(generator, /loader\.load_reference\(args\.source_root, args\.wheel\)/);
    assert.match(generator, /reference\["forward"\]\(backend, tensor\)/);
    assert.match(generator, /modelInferenceExecuted=True, androidNativeInferenceExecuted=False/);
    assert.doesNotMatch(generator, /SyntheticInterpreter\(|subprocess\.|pip install|urlopen|requests\./);
});

test('actual media remains private and all four positive exercise/view conditions are required', () => {
    assert.match(generator, /build not in destination\.parents/);
    assert.match(generator, /rightsCleared/);
    assert.match(generator, /covered != REQUIRED/);
    assert.match(generator, /not detections or not any/);
    assert.match(generator, /PROVISIONAL_NOT_MEASURED/);
    assert.match(generator, /detectionCount=0, classMatch="EXACT"/);
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
