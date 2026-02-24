# Assignment 2: Document Similarity using MapReduce

**Name:** Yamini Buyya

**Student ID:** [801482615]

## Approach and Implementation

### Mapper Design

**Input Key-Value Pair:**
- Key: Byte offset (provided by Hadoop, not used)
- Value: A line of text in the format `DocumentName word1 word2 word3 ...`

**Logic:**
The Mapper processes each input line by:
1. Parsing the line to extract the document name and content
2. Tokenizing the content into individual words (converted to lowercase)
3. Storing unique words in a Set to avoid duplicates
4. Emitting each unique word paired with the document name that contains it

**Output Key-Value Pair:**
- Key: A word (Text)
- Value: The document name containing that word (Text)

**Example:**
For input line: `Document1 This is a sample`
The Mapper emits:
- `(this, Document1)`
- `(is, Document1)`
- `(a, Document1)`
- `(sample, Document1)`

**How it helps:**
By emitting `(word, documentName)` pairs, the Mapper enables the shuffle/sort phase to group all documents that share the same word together. This grouping is essential for calculating which documents have overlapping vocabulary, which forms the basis of the Jaccard similarity calculation.

---

### Reducer Design

**Input Key-Value Pair:**
- Key: A word (Text)
- Value: Iterator of document names (Text) - all documents containing this word

**Processing Logic:**
The Reducer operates in two phases:

**Phase 1 - reduce() method (called once per word):**
1. Receives a word and all documents containing that word
2. Builds a data structure (`documentWords` Map) that tracks which words belong to each document
3. For each document in the values, adds the current word to that document's word set

**Phase 2 - cleanup() method (called once after all reduce() calls):**
1. Retrieves all document names from the `documentWords` Map
2. Generates all unique document pairs using nested loops
3. For each pair:
   - Calculates **intersection**: words common to both documents (using `Set.retainAll()`)
   - Calculates **union**: all unique words across both documents (using `Set.addAll()`)
   - Computes **Jaccard Similarity** = |intersection| / |union|
   - Formats the output as "DocumentX, DocumentY    Similarity: 0.XX"

**Output Key-Value Pair:**
- Key: Document pair (e.g., "Document1, Document2")
- Value: Similarity score (e.g., "Similarity: 0.40")

**Jaccard Similarity Calculation:**
```
Jaccard Similarity = |A ∩ B| / |A ∪ B|

Where:
- A ∩ B = words common to both documents
- A ∪ B = all unique words in both documents
```

**Example:**
- Document1 words: {this, is, sample}
- Document2 words: {this, another, sample}
- Intersection: {this, sample} = 2 words
- Union: {this, is, sample, another} = 4 words
- Similarity = 2/4 = 0.50

---

### Overall Data Flow

**1. Input Phase:**
- Input file stored in HDFS at `/input/data/input.txt`
- Contains lines in format: `DocumentName word1 word2 word3 ...`

**2. Map Phase:**
- Mapper reads each line sequentially
- Extracts document name and words
- Emits `(word, documentName)` for each unique word in each document
- Example: For 3 documents with ~10 words each, emits ~30 key-value pairs

**3. Shuffle and Sort Phase (Automatic by Hadoop):**
- Groups all values by key (word)
- Sorts keys alphabetically
- Result: `(word, [doc1, doc2, doc3, ...])` for each word
- Example: `(sample, [Document1, Document3])` means both documents contain "sample"

**4. Reduce Phase:**
- **reduce()** method called once per word
   - Builds `documentWords` Map tracking all words for each document
   - Accumulates data across all reduce() calls

- **cleanup()** method called once after all words processed
   - Generates all document pairs
   - Calculates Jaccard similarity for each pair
   - Emits final similarity scores

**5. Output Phase:**
- Results written to HDFS at `/output1/part-r-00000`
- Contains one line per document pair with similarity score
- `_SUCCESS` file indicates job completion

**Data Flow Diagram:**
```
Input File
    ↓
[Mapper: Parse & Emit (word, doc)]
    ↓
[Shuffle/Sort: Group by word]
    ↓
[Reducer Phase 1: Build document-word map]
    ↓
[Reducer Phase 2: Calculate similarities]
    ↓
Output File
```

---

## Setup and Execution

### 1. **Start the Hadoop Cluster**

Run the following command to start the Hadoop cluster:
```bash
docker compose up -d
```

### 2. **Build the Code**

Build the code using Maven:
```bash
mvn clean package
```

### 3. **Copy JAR to Docker Container**

Copy the JAR file to the Hadoop ResourceManager container:
```bash
docker cp target/DocumentSimilarity-0.0.1-SNAPSHOT.jar resourcemanager:/opt/hadoop-3.2.1/share/hadoop/mapreduce/
```

### 4. **Move Dataset to Docker Container**

Copy the dataset to the Hadoop ResourceManager container:
```bash
docker cp shared-folder/input/data/input.txt resourcemanager:/opt/hadoop-3.2.1/share/hadoop/mapreduce/
```

### 5. **Connect to Docker Container**

Access the Hadoop ResourceManager container:
```bash
docker exec -it resourcemanager /bin/bash
```

Navigate to the Hadoop directory:
```bash
cd /opt/hadoop-3.2.1/share/hadoop/mapreduce/
```

### 6. **Set Up HDFS**

Create a folder in HDFS for the input dataset:
```bash
hadoop fs -mkdir -p /input/data
```

Copy the input dataset to the HDFS folder:
```bash
hadoop fs -put ./input.txt /input/data
```

### 7. **Execute the MapReduce Job**

Run your MapReduce job using the following command:
```bash
hadoop jar /opt/hadoop-3.2.1/share/hadoop/mapreduce/DocumentSimilarity-0.0.1-SNAPSHOT.jar com.example.controller.DocumentSimilarityDriver /input/data/input.txt /output1
```

### 8. **View the Output**

To view the output of your MapReduce job, use:
```bash
hadoop fs -cat /output1/*
```

### 9. **Copy Output from HDFS to Local OS**

To copy the output from HDFS to your local machine:

1. Use the following command to copy from HDFS:
```bash
    hdfs dfs -get /output1 /opt/hadoop-3.2.1/share/hadoop/mapreduce/
```

2. Exit the container and use Docker to copy from the container to your local machine:
```bash
   exit 
```
```bash
    docker cp resourcemanager:/opt/hadoop-3.2.1/share/hadoop/mapreduce/output1/ shared-folder/output/
```

3. Commit and push to your repo so that we can see your output

---

## Challenges and Solutions

### Challenge 1: Incorrect Directory Structure
**Problem:** Initial Maven build showed "No sources to compile" because Java files were located at `src/main/com/example/` instead of the required `src/main/java/com/example/`.

**Solution:**
- Created the missing `java` directory: `mkdir -p src/main/java`
- Moved the `com` folder into the correct location: `mv src/main/com src/main/java/`
- Rebuilt the project with `mvn clean package`
- Verified compilation success by checking that all 3 source files were compiled

**Lesson Learned:** Maven follows strict conventions for project structure. The source code must be in `src/main/java/` for Maven to recognize and compile it.

---

### Challenge 2: ClassNotFoundException During Job Execution
**Problem:** First attempt to run the MapReduce job failed with:
```
Exception in thread "main" java.lang.ClassNotFoundException: com.example.controller.DocumentSimilarityDriver
```

**Root Cause:** The JAR file was created before fixing the directory structure, so it contained only Maven metadata but no compiled Java classes.

**Solution:**
- Fixed the directory structure (see Challenge 1)
- Rebuilt the project to compile the classes
- Verified JAR contents using: `jar tf target/DocumentSimilarity-0.0.1-SNAPSHOT.jar | grep -E "(Mapper|Reducer|Driver)"`
- Confirmed all three classes were present in the JAR
- Copied the new JAR to the Docker container, overwriting the empty one

**Lesson Learned:** Always verify that the JAR file contains the expected compiled classes before deploying to Hadoop.

---

### Challenge 3: Understanding the Two-Phase Reducer Design
**Problem:** Initially, it wasn't clear how to calculate similarity between document pairs when the Reducer receives data grouped by words, not by document pairs.

**Solution:**
Implemented a two-phase approach in the Reducer:
1. **Phase 1 (reduce() method):** Accumulate all words for each document in a shared Map across all reduce() calls
2. **Phase 2 (cleanup() method):** Once all words are collected, generate document pairs and calculate Jaccard similarity

This design pattern leverages Hadoop's lifecycle methods:
- `reduce()` is called multiple times (once per word)
- `cleanup()` is called exactly once after all reduce() calls complete

**Lesson Learned:** The Reducer's `cleanup()` method is powerful for post-processing accumulated data after all individual reduce() calls complete.

---

### Challenge 4: Algorithm Design - Choosing Between Pair-Based vs Word-Based Approach
**Problem:** Two possible approaches existed:
1. Emit document pairs in the Mapper
2. Emit words in the Mapper and create pairs in the Reducer

**Solution:**
Chose the word-based approach (option 2) because:
- Simpler Mapper logic
- More efficient - Hadoop's shuffle/sort automatically groups documents by shared words
- Clearer separation of concerns: Mapper extracts features, Reducer computes similarity
- Follows the standard MapReduce pattern for similarity calculations

**Lesson Learned:** Let Hadoop's built-in shuffle/sort mechanism do the heavy lifting of grouping related data.

---

## Sample Input

**Input from `input.txt`**
```
Document1 Hi, this is yamini buyya and this is a sample document containing words
Document2 This is also Another document that also has words
Document3 Sample text with different words and this is third document
```

## Sample Output

**Expected Output Format:**
```
Document1, Document2 Similarity: 0.XX
Document1, Document3 Similarity: 0.XX
Document2, Document3 Similarity: 0.XX
```

## Obtained Output

**Actual output from `part-r-00000`:**
```
Document3, Document2    Similarity: 0.29
Document3, Document1    Similarity: 0.40
Document2, Document1    Similarity: 0.27
```

**Analysis:**
- **Document1 and Document3** have the highest similarity (0.40 or 40%), meaning they share 40% of their combined vocabulary
- **Document2 and Document3** are moderately similar (0.29 or 29%)
- **Document1 and Document2** are the least similar (0.27 or 27%)

This makes sense given the input data:
- Document1 and Document3 both use words like "this", "is", "sample", "document", "words"
- Document2 has more unique words like "also", "another", "that", "has"

**Job Statistics:**
- Map input records: 3 (three documents)
- Map output records: 29 (total unique word-document pairs)
- Reduce input groups: 19 (19 unique words across all documents)
- Reduce output records: 3 (three document pairs)
- Job completed successfully in ~24 seconds