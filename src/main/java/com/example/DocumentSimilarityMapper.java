package com.example;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import java.io.IOException;
import java.util.*;

public class DocumentSimilarityMapper extends Mapper<Object, Text, Text, Text> {

    @Override
    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        String line = value.toString().trim();
        if (line.isEmpty()) return;

        String[] parts = line.split("\\s+", 2);
        if (parts.length < 2) return;

        String docName = parts[0];
        String content = parts[1];

        Set<String> words = new HashSet<>();
        String[] tokens = content.toLowerCase().split("\\s+");
        for (String token : tokens) {
            if (!token.isEmpty()) {
                words.add(token);
            }
        }

        for (String word : words) {
            context.write(new Text(word), new Text(docName));
        }
    }
}