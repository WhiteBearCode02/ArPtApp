"""EXP03 identity/static tensor inspection. Reuses the bounded parser, never EXP02 evidence.

Optional --python-native-smoke invokes the installed LOCAL official runtime with synthetic zero
input only. It is not Android, Golden equivalence, semantic accuracy or camera performance.
No install, network, model copy, photo access or verification promotion.
"""
import argparse
import importlib.util
import json
from pathlib import Path

MODEL_ID = "yolo26n_exp03_final_fp32"
MODEL_NAME = "airptcoach_yolo26n_exp03_final_fp32.tflite"
MODEL_BYTES = 9894032
MODEL_SHA256 = "3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167"
spec = importlib.util.spec_from_file_location("shared_tflite_metadata", Path(__file__).with_name("inspect-exp02-model.py"))
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)

def inspect(model: Path, python_native_smoke=False):
    result = shared.inspect_model(model, model_name=MODEL_NAME, model_bytes=MODEL_BYTES, model_sha256=MODEL_SHA256)
    result["model"]["id"] = MODEL_ID
    result["pythonNativeSmoke"] = "NOT_RUN"
    if python_native_smoke:
        from ai_edge_litert.interpreter import Interpreter
        from importlib.metadata import version
        import numpy as np
        runtime = Interpreter(model_path=str(model.resolve()), num_threads=2)
        runtime.allocate_tensors()
        inputs, outputs = runtime.get_input_details(), runtime.get_output_details()
        assert len(inputs) == len(outputs) == 1
        assert inputs[0]["shape"].tolist() == [1, 3, 640, 640] and inputs[0]["dtype"] == np.float32
        assert outputs[0]["shape"].tolist() == [1, 6, 8400] and outputs[0]["dtype"] == np.float32
        runtime.set_tensor(inputs[0]["index"], np.zeros((1, 3, 640, 640), dtype=np.float32))
        runtime.invoke()
        output = runtime.get_tensor(outputs[0]["index"])
        assert bool(np.isfinite(output).all())
        result.update(pythonNativeSmoke="PASS_SYNTHETIC_ZERO_INPUT_ONLY", pythonRuntime=version("ai-edge-litert"),
                      pythonNativeInferenceCount=1, androidNativeInferenceExecuted=False, contractStatus="NOT_VERIFIED")
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--python-native-smoke", action="store_true")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[1]
    output = shared.private_output_path(args.output, repository) if args.output else None
    result = inspect(args.model, args.python_native_smoke)
    document = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output = shared.private_output_path(output, repository)
        with output.open("x", encoding="utf-8") as stream:
            stream.write(document)
    print(document, end="")

if __name__ == "__main__":
    main()
