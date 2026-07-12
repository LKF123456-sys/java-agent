package com.ailearn.common;

import java.util.ArrayList;
import java.util.List;

public class StreamUtils {

    private StreamUtils() {
    }

    public static String[] splitIntoChunks(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int chunkSize = 2 + (int) (Math.random() * 3);
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            i = end;
        }
        return chunks.toArray(new String[0]);
    }
}
