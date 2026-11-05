import com.example.arptapp.domain.detection.*;
import com.google.gson.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Read-only model/media use: executes the ACTUAL compiled Kotlin preprocessors in a host JVM.
 * No Android, camera, inference, network or DB access. Original private fixtures are never changed.
 * Only a NEW ignored JSON with scalar counters/first 20 mismatches is written, not any raster/tensor.
 * Run with existing compiled debug classes, Kotlin stdlib and Gson on java's classpath.
 */
class Exp03PreprocessingAudit {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int ELEMENTS = 3 * 640 * 640;
    static String sha(ByteBuffer bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes.asReadOnlyBuffer());
        return HexFormat.of().formatHex(digest.digest());
    }
    static void check(boolean value, String message) { if (!value) throw new IllegalArgumentException(message); }
    static Path privatePath(Path root, Path requested) throws Exception {
        Path path = requested.toAbsolutePath().normalize();
        check(path.startsWith(root.resolve("local_validation/exp03_golden")) || path.startsWith(root.resolve("build")), "Private path required");
        for (Path part = path; !part.equals(root); part = part.getParent()) {
            check(!Files.isSymbolicLink(part), "No private symlinks");
            if (Files.exists(part)) check(part.toRealPath().equals(part), "No private junctions");
        }
        String relative = root.relativize(path).toString().replace('\\','/');
        Process ignored = new ProcessBuilder("git", "-C", root.toString(), "check-ignore", "--quiet", "--no-index", "--", relative).start();
        check(ignored.waitFor(30, TimeUnit.SECONDS) && ignored.exitValue() == 0, "Private path must be Git ignored");
        Process tracked = new ProcessBuilder("git", "-C", root.toString(), "ls-files", "--", relative).start();
        check(tracked.waitFor(30, TimeUnit.SECONDS) && tracked.exitValue() == 0 && tracked.getInputStream().readAllBytes().length == 0, "Tracked private path forbidden");
        return path;
    }
    static byte[] read(Path directory, String name, int size, String hash) throws Exception {
        check(name.matches("[a-z0-9][a-z0-9_.-]{0,127}"), "Flat fixture basename required");
        Path file = directory.resolve(name);
        check(!Files.isSymbolicLink(file) && file.toRealPath().getParent().equals(directory) && Files.size(file) == size, "Fixture bounds/path mismatch");
        byte[] bytes = Files.readAllBytes(file);
        check(sha(ByteBuffer.wrap(bytes)).equals(hash), "Private fixture SHA mismatch");
        return bytes;
    }
    static Map<String,Object> compare(ByteBuffer expected, YoloPreparedInput prepared) throws Exception {
        ByteBuffer actual = prepared.getTensor().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        Exp02FrameTransform geometry = (Exp02FrameTransform)prepared.getTransform();
        long[] channel = new long[3];
        long padding = 0, nativePixels = 0, resampled = 0, mismatches = 0;
        double max = 0, maxByte = 0;
        List<Map<String,Object>> first = new ArrayList<>();
        boolean resized = geometry.getResizedWidth() != geometry.getOriginalWidth() || geometry.getResizedHeight() != geometry.getOriginalHeight();
        check(expected.remaining() == ELEMENTS * 4 && actual.remaining() == ELEMENTS * 4, "Tensor bounds mismatch");
        for (int i = 0; i < ELEMENTS; i++) {
            float a = expected.getFloat(i*4), b = actual.getFloat(i*4);
            check(Float.isFinite(a) && Float.isFinite(b), "Nonfinite tensor");
            int c = i / (640*640), x = i % 640, y = (i % (640*640))/640;
            boolean image = x >= geometry.getPadX() && x < geometry.getPadX()+geometry.getResizedWidth()
                && y >= geometry.getPadY() && y < geometry.getPadY()+geometry.getResizedHeight();
            double difference = Math.abs((double)a-b);
            max = Math.max(max, difference); maxByte = Math.max(maxByte, difference*255.0);
            if (Float.floatToRawIntBits(a) != Float.floatToRawIntBits(b)) {
                mismatches++; channel[c]++;
                if (!image) padding++; else if (resized) resampled++; else nativePixels++;
                if (first.size() < 20) {
                    Map<String,Object> point = new LinkedHashMap<>();
                    point.put("channel", "RGB".substring(c,c+1)); point.put("x",x); point.put("y",y);
                    point.put("region", !image ? "PADDING" : resized ? "RESAMPLED" : "NATIVE_SIZE");
                    point.put("expectedFloat",a); point.put("actualFloat",b);
                    point.put("expectedUint8",Math.round(a*255)); point.put("actualUint8",Math.round(b*255));
                    first.add(point);
                }
            }
        }
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("elements",ELEMENTS); report.put("mismatches",mismatches);
        report.put("channelMismatches",channel); report.put("paddingMismatches",padding);
        report.put("nativeSizeMismatches",nativePixels); report.put("resampledMismatches",resampled);
        report.put("maxFloatDifference",max); report.put("maxUint8EquivalentDifference",maxByte);
        report.put("tensorSha256",sha(actual)); report.put("referenceSha256",sha(expected));
        report.put("byteIdentical",mismatches==0); report.put("first20Mismatches",first);
        return report;
    }
    public static void main(String[] args) throws Exception {
        check(args.length == 3, "Pass repository root, private source bundle, NEW private JSON output");
        Path root = Path.of(args[0]).toRealPath();
        Path bundle = privatePath(root, Path.of(args[1]));
        Path output = privatePath(root, Path.of(args[2]));
        check(!Files.exists(output), "Preserve existing audit evidence");
        Path referenceFile = bundle.resolve("reference.json");
        check(Files.size(referenceFile) <= 2_097_152, "Reference too large");
        JsonObject reference = JsonParser.parseString(Files.readString(referenceFile)).getAsJsonObject();
        check(reference.getAsJsonObject("model").get("sha256").getAsString().equals(KnownYoloModels.INSTANCE.getEXP03().getSha256()), "EXP03 reference required");
        check(reference.get("schemaVersion").getAsInt()==1 && reference.get("validationPurpose").getAsString().equals("EXP03_ACTUAL_IMAGE_GOLDEN")
            && reference.get("modelInferenceExecuted").getAsBoolean(), "Actual EXP03 provenance required");
        check(reference.getAsJsonArray("samples").size()>=4 && reference.getAsJsonArray("samples").size()<=64, "Bounded sample coverage required");
        List<Map<String,Object>> results = new ArrayList<>();
        for (JsonElement element : reference.getAsJsonArray("samples")) {
            JsonObject sample = element.getAsJsonObject();
            check(sample.get("rightsCleared").getAsBoolean() && !sample.get("rightsBasis").getAsString().isBlank(), "Explicit input rights required");
            int width = sample.get("width").getAsInt(), height = sample.get("height").getAsInt();
            Exp02FrameTransform.Companion.create(width,height); // bound before allocation
            byte[] rgb = read(bundle, sample.get("rgbFile").getAsString(), width*height*3, sample.get("rgbSha256").getAsString());
            int[] pixels = new int[width*height];
            for (int i=0;i<pixels.length;i++) pixels[i] = 0xff000000 | ((rgb[3*i]&255)<<16) | ((rgb[3*i+1]&255)<<8) | (rgb[3*i+2]&255);
            ByteBuffer expected = ByteBuffer.wrap(read(bundle,sample.get("inputTensorFile").getAsString(),ELEMENTS*4,
                sample.get("inputTensorSha256").getAsString())).order(ByteOrder.LITTLE_ENDIAN);
            Exp02RgbFrame frame = new Exp02RgbFrame(width,height,pixels);
            Map<String,Object> row = new LinkedHashMap<>(); row.put("id",sample.get("id").getAsString());
            row.put("legacy",compare(expected,new Exp02RgbPreprocessor().preprocess(frame)));
            row.put("corrected",compare(expected,new YoloOpenCvLinearRgbPreprocessor().preprocess(frame)));
            results.add(row);
        }
        Map<String,Object> document = new LinkedHashMap<>();
        document.put("schemaVersion",1); document.put("sourceReferenceSha256",sha(ByteBuffer.wrap(Files.readAllBytes(referenceFile))));
        document.put("actualKotlinClassesExecuted",true); document.put("modelInferenceExecuted",false);
        document.put("samples",results);
        Files.createDirectories(output.getParent());
        Files.writeString(output,GSON.toJson(document)+"\n",StandardOpenOption.CREATE_NEW);
        System.out.println("Private preprocessing audit complete: "+results.size()+" samples, both actual Kotlin implementations; no inference");
    }
}
