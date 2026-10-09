package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.nexus.service.SemanticEncoder;

class SemanticEncoderTest {

  @Test
  void pretrainedRussianSemanticsAndReferenceTokenizer() throws Exception {
    var model = new SemanticEncoder("./models/rubert-tiny2");
    model.initialize();
    try {
      assertTrue(
        model.available(),
        "Download the pinned model with scripts/download-model.sh or .ps1"
      );
      var fixtures = new ObjectMapper().readTree(
        getClass().getResourceAsStream("/tokenizer-reference.json")
      );
      for (var sample : fixtures) {
        var expected = new java.util.ArrayList<Long>();
        sample.path("ids").forEach(n -> expected.add(n.asLong()));
        assertEquals(
          expected,
          model.tokenize(sample.path("text").asText()),
          sample.path("text").asText()
        );
      }
      String[][] samples = {
        {
          "Люблю читать романы и обсуждать литературу.",
          "Обожаю книги и разговоры о писателях.",
          "Ремонтирую двигатели автомобилей в гараже.",
        },
        {
          "По выходным хожу в горы с рюкзаком и палаткой.",
          "Мне нравятся пешие походы и ночёвки на природе.",
          "Разрабатываю финансовые отчёты и бухгалтерские таблицы.",
        },
        {
          "Ценю честность и открытое обсуждение чувств в отношениях.",
          "Для меня важно доверять партнёру и искренне говорить о переживаниях.",
          "Люблю собирать компьютеры и программировать приложения.",
        },
      };
      for (var row : samples) {
        var a = model.encode(row[0]);
        assertEquals(312, a.length);
        assertEquals(1, SemanticEncoder.cosine(a, a), 1e-6);
        double near = SemanticEncoder.cosine(a, model.encode(row[1])),
          far = SemanticEncoder.cosine(a, model.encode(row[2]));
        System.out.println(
          "Semantic sample: related=" + near + " unrelated=" + far
        );
        assertTrue(
          near > far,
          "Meaningful paraphrase must rank before unrelated text"
        );
      }
      String first = "Люблю природу и походы. ".repeat(80);
      assertTrue(
        SemanticEncoder.cosine(
          model.encode(first),
          model.encode(first + "Разработка компьютерных программ. ".repeat(80))
        ) < 0.99,
        "Text after the first chunk must influence the vector"
      );
    } finally {
      model.close();
    }
  }
}
