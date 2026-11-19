package com.aiguardian.domain.verification;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u3000]+", "")
                .replace('。', '.').replace('，', ',').replace('；', ';')
                .replace('：', ':').replace('！', '!').replace('？', '?').replace('、', ',')
                .replace('“', '"').replace('”', '"').replace('「', '"').replace('」', '"')
                .replace('‘', '\'').replace('’', '\'')
                .replaceAll("(?<=\\d),(?=\\d{3})", "").toLowerCase(Locale.ROOT);
    }
}
