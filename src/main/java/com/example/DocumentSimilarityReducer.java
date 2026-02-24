package com.example;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import java.io.IOException;
import java.util.*;

public class DocumentSimilarityReducer extends Reducer<Text, Text, Text, Text> {

    private Map<String, Set<String>> documentWords = new HashMap<>();

    @Override
    public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        // key = word
        // values = list of documents containing this word

        List<String> docs = new ArrayList<>();
        for (Text val : values) {
            String docName = val.toString();
            if (!docs.contains(docName)) {
                docs.add(docName);
            }

            // Track which words each document has
            if (!documentWords.containsKey(docName)) {
                documentWords.put(docName, new HashSet<>());
            }
            documentWords.get(docName).add(key.toString());
        }
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        // Now calculate similarity for all document pairs
        List<String> docNames = new ArrayList<>(documentWords.keySet());

        for (int i = 0; i < docNames.size(); i++) {
            for (int j = i + 1; j < docNames.size(); j++) {
                String doc1 = docNames.get(i);
                String doc2 = docNames.get(j);

                Set<String> words1 = documentWords.get(doc1);
                Set<String> words2 = documentWords.get(doc2);

                // Calculate intersection
                Set<String> intersection = new HashSet<>(words1);
                intersection.retainAll(words2);

                // Calculate union
                Set<String> union = new HashSet<>(words1);
                union.addAll(words2);

                // Calculate Jaccard similarity
                double similarity = union.size() > 0 ?
                        (double) intersection.size() / union.size() : 0.0;

                // Format output
                String pairKey = doc1 + ", " + doc2;
                String simValue = String.format("Similarity: %.2f", similarity);

                context.write(new Text(pairKey), new Text(simValue));
            }
        }
    }
}