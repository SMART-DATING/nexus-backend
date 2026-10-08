package ru.nexus.service;

import ai.onnxruntime.*;
import jakarta.annotation.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.LongStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local pretrained RuBERT-tiny2: cased WordPiece, CLS pooling, L2 normalization. */
@Component
public class SemanticEncoder {

  public static final String VERSION =
    "rubert-tiny2:5e6f7f225ff590999fc409e3e747c382d79d94b5:cls:256";
  private final Path directory;
  private final Map<String, Integer> vocabulary = new HashMap<>();
  private OrtEnvironment environment;
  private OrtSession session;

  public SemanticEncoder(
    @Value("${nexus.semantic.model-dir:./models/rubert-tiny2}") String directory
  ) {
    this.directory = Path.of(directory);
  }

  @PostConstruct
  public void initialize() {
    if (
      !Files.isRegularFile(directory.resolve("model.onnx")) ||
      !Files.isRegularFile(directory.resolve("vocab.txt"))
    ) {
      org.slf4j.LoggerFactory.getLogger(getClass()).warn(
        "Semantic model missing at {}; run scripts/download-model.ps1 or download-model.sh",
        directory.toAbsolutePath()
      );
      return;
    }
    try {
      var words = Files.readAllLines(directory.resolve("vocab.txt"));
      for (int i = 0; i < words.size(); i++) vocabulary.put(words.get(i), i);
      environment = OrtEnvironment.getEnvironment();
      try (var options = new OrtSession.SessionOptions()) {
        options.setIntraOpNumThreads(2);
        options.setInterOpNumThreads(1);
        session = environment.createSession(
          directory.resolve("model.onnx").toString(),
          options
        );
      }
      encode("Проверка смыслового подбора");
    } catch (Exception | LinkageError e) {
      throw new IllegalStateException(
        "Cannot load local semantic model at " + directory.toAbsolutePath(),
        e
      );
    }
  }

  public boolean available() {
    return session != null;
  }

  public List<Long> tokenize(String text) {
    var clean = new StringBuilder();
    Normalizer.normalize(text, Normalizer.Form.NFC)
      .codePoints()
      .forEach(cp -> {
        if (
          Character.isWhitespace(cp) || Character.isSpaceChar(cp)
        ) clean.append(' ');
        else if (
          Character.getType(cp) == Character.CONTROL ||
          Character.getType(cp) == Character.FORMAT ||
          cp == 0 ||
          cp == 0xfffd
        ) {
        } else if (punctuation(cp) || chinese(cp)) clean
          .append(' ')
          .appendCodePoint(cp)
          .append(' ');
        else clean.appendCodePoint(cp);
      });
    List<Long> result = new ArrayList<>();
    for (String word : clean.toString().trim().split(" +")) {
      if (word.isEmpty()) continue;
      if (word.codePointCount(0, word.length()) > 100) {
        result.add((long) vocabulary.get("[UNK]"));
        continue;
      }
      List<Long> pieces = new ArrayList<>();
      int start = 0;
      boolean unknown = false;
      while (start < word.length()) {
        int end = word.length();
        Integer found = null;
        while (end > start) {
          found = vocabulary.get(
            (start == 0 ? "" : "##") + word.substring(start, end)
          );
          if (found != null) break;
          end = word.offsetByCodePoints(end, -1);
        }
        if (found == null) {
          unknown = true;
          break;
        }
        pieces.add(found.longValue());
        start = end;
      }
      if (unknown) result.add((long) vocabulary.get("[UNK]"));
      else result.addAll(pieces);
    }
    return result;
  }

  private static boolean punctuation(int cp) {
    int t = Character.getType(cp);
    return (
      (cp >= 33 && cp <= 47) ||
      (cp >= 58 && cp <= 64) ||
      (cp >= 91 && cp <= 96) ||
      (cp >= 123 && cp <= 126) ||
      t == Character.CONNECTOR_PUNCTUATION ||
      t == Character.DASH_PUNCTUATION ||
      t == Character.START_PUNCTUATION ||
      t == Character.END_PUNCTUATION ||
      t == Character.INITIAL_QUOTE_PUNCTUATION ||
      t == Character.FINAL_QUOTE_PUNCTUATION ||
      t == Character.OTHER_PUNCTUATION
    );
  }

  private static boolean chinese(int cp) {
    return (
      (cp >= 0x4e00 && cp <= 0x9fff) ||
      (cp >= 0x3400 && cp <= 0x4dbf) ||
      (cp >= 0x20000 && cp <= 0x2fa1f) ||
      (cp >= 0xf900 && cp <= 0xfaff)
    );
  }

  public synchronized double[] encode(String text) {
    if (!available()) throw NexusService.fail(
      503,
      "Модель подбора ещё не установлена. Запустите установку модели по инструкции."
    );
    var tokens = tokenize(text);
    if (tokens.isEmpty()) throw NexusService.fail(
      400,
      "Добавьте содержательный текст о себе"
    );
    double[] combined = null;
    try {
      for (int start = 0; start < tokens.size(); start += 254) {
        int count = Math.min(254, tokens.size() - start);
        long[][] ids = new long[1][count + 2];
        ids[0][0] = vocabulary.get("[CLS]");
        ids[0][count + 1] = vocabulary.get("[SEP]");
        for (int i = 0; i < count; i++) ids[0][i + 1] = tokens.get(start + i);
        long[][] mask = {
            LongStream.range(0, count + 2)
              .map(x -> 1)
              .toArray(),
          },
          types = new long[1][count + 2];
        try (
          var input = OnnxTensor.createTensor(environment, ids);
          var attention = OnnxTensor.createTensor(environment, mask);
          var segments = OnnxTensor.createTensor(environment, types)
        ) {
          Map<String, OnnxTensor> inputs = new HashMap<>();
          inputs.put("input_ids", input);
          inputs.put("attention_mask", attention);
          if (session.getInputNames().contains("token_type_ids")) inputs.put(
            "token_type_ids",
            segments
          );
          try (var output = session.run(inputs)) {
            var hidden = (float[][][]) output
              .get("last_hidden_state")
              .orElseGet(() -> output.get(0))
              .getValue();
            double[] vector = new double[hidden[0][0].length];
            for (int i = 0; i < vector.length; i++) vector[i] = hidden[0][0][i];
            normalize(vector);
            if (combined == null) combined = new double[vector.length];
            for (int i = 0; i < vector.length; i++) combined[i] +=
              vector[i] * count;
          }
        }
      }
      normalize(combined);
      return combined;
    } catch (OrtException e) {
      throw new IllegalStateException("Local semantic inference failed", e);
    }
  }

  public static void normalize(double[] vector) {
    double norm = 0;
    for (double v : vector) norm += v * v;
    norm = Math.sqrt(norm);
    if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
  }

  public static double cosine(double[] a, double[] b) {
    if (a.length != b.length) throw new IllegalArgumentException(
      "Incompatible embedding dimensions"
    );
    double v = 0;
    for (int i = 0; i < a.length; i++) v += a[i] * b[i];
    return Math.max(-1, Math.min(1, v));
  }

  @PreDestroy
  public void close() throws OrtException {
    if (session != null) session.close();
  }
}
