---
name: "rag-knowledge-base"
description: "Builds a RAG (Retrieval-Augmented Generation) knowledge base with Spring AI: vector store, document ingestion from a materials folder, and query endpoints. Invoke when user wants to create/build a RAG system, ingest documents, or add a knowledge base to the Spring AI project."
---

# RAG Knowledge Base Builder

This skill builds a complete RAG (Retrieval-Augmented Generation) knowledge base on top of the existing Spring AI + Ollama project. It creates a materials entry point for the user to drop documents, ingests them into a vector store, and exposes query endpoints.

## Target Project Context

- Spring Boot 3.4.5 + Spring AI 1.0.0
- Ollama as chat & embedding provider (base-url: http://localhost:11434)
- Package root: `org.example.ai`
- Existing global `ChatClient` bean in `Main.java`
- Config file: `src/main/resources/application.yml`

## Verified Spring AI 1.0.0 GA API Reference

These artifact names and class locations were verified against the actual `spring-ai-bom:1.0.0` POM and JARs. Do NOT use the old milestone names.

| Purpose | Artifact (managed by spring-ai-bom 1.0.0) | Key Class |
|---|---|---|
| Ollama chat + embedding starter | `spring-ai-starter-model-ollama` | `OllamaChatModel`, `OllamaEmbeddingModel` |
| Vector store core (in-memory) | `spring-ai-vector-store` | `org.springframework.ai.vectorstore.SimpleVectorStore` |
| RAG advisor | `spring-ai-advisors-vector-store` | `org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor` |
| Document readers (PDF/Word/TXT/MD) | `spring-ai-tika-document-reader` | `org.springframework.ai.reader.tika.TikaDocumentReader` |

IMPORTANT naming changes in 1.0.0 GA (vs old milestones):
- Starter renamed: `spring-ai-ollama-spring-boot-starter` → `spring-ai-starter-model-ollama`
- Class renamed: `SimpleInMemoryVectorStore` → `SimpleVectorStore`
- Package moved: `QuestionAnswerAdvisor` from `...advisor` → `...advisor.vectorstore`
- `QuestionAnswerAdvisor` has NO 2-arg constructor; use the builder: `QuestionAnswerAdvisor.builder(vectorStore).searchRequest(SearchRequest.builder().topK(4).build()).build()`

## Build Steps

When invoked, follow these steps IN ORDER. Adapt file paths to the actual project layout discovered via search tools.

### Step 1: Verify & Add Dependencies

Check `pom.xml`. Ensure `spring-ai-bom` 1.0.0 is in `<dependencyManagement>`. The Ollama starter artifact is `spring-ai-starter-model-ollama` (NOT the old `spring-ai-ollama-spring-boot-starter`, which is absent from the 1.0.0 BOM).

Add these dependencies inside `<dependencies>` if missing:

```xml
<!-- Spring AI Ollama 支持（1.0.0 GA 已重命名） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
<!-- Spring AI 向量库核心（含 SimpleVectorStore） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>
<!-- Spring AI RAG Advisor（含 QuestionAnswerAdvisor） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
<!-- Spring AI 文档读取（PDF/Word/TXT/Markdown via Tika） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
```

If the user wants persistent storage instead of in-memory, add a vector-store starter (e.g. `spring-ai-starter-vector-store-pgvector`) and skip the manual `VectorStore` bean in Step 4. Default to **in-memory (SimpleVectorStore)** unless the user explicitly asks for persistence.

### Step 2: Create Materials Entry Point

Create the directory `src/main/resources/materials/` and add a `README.txt` explaining accepted formats. This is the entry point where the user fills in materials.

`src/main/resources/materials/README.txt` content:
```
RAG 知识库材料目录
==================
请将你的知识库材料放入此目录，支持的格式：
  - .txt        纯文本
  - .md         Markdown
  - .pdf        PDF 文档
  - .doc/.docx  Word 文档

使用流程：
  1. 将材料文件拷贝到本目录
  2. 启动应用后调用入库接口：
       POST http://localhost:8080/rag/ingest
  3. 入库成功后即可查询：
       GET  http://localhost:8080/rag/query?question=你的问题

注意：
  - 入库操作会读取本目录下所有文件并分块存入向量库
  - 内存向量库重启后数据会丢失，需重新入库
  - README.txt 会被自动跳过，不会入库
```

### Step 3: Configure Embedding Model in application.yml

Add Ollama embedding config. The user must pull an embedding model first:
`ollama pull nomic-embed-text` (recommended, lightweight) or `bge-m3`.

Append to `spring.ai.ollama` in `application.yml`:
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2.5:7b-instruct-q6_K
          temperature: 0.7
      embedding:
        options:
          model: nomic-embed-text   # 需先 ollama pull nomic-embed-text
```

### Step 4: Create Vector Store Configuration

Create `org.example.ai.config.RagConfig.java`:

```java
package org.example.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

If using a persistent store (e.g. PgVector), remove this bean and let autoconfiguration handle it; add datasource config in yml.

### Step 5: Create Document Ingestion Service

Create `org.example.ai.service.RagService.java` with:
- `ingestMaterials()`: scans `classpath:materials/*`, reads each file with `TikaDocumentReader`, splits with `TokenTextSplitter`, and calls `vectorStore.add(documents)`. Skips `README*` files.
- `query(String question)`: uses `ChatClient` with a `QuestionAnswerAdvisor` (builder pattern) over the `VectorStore` to retrieve top-k relevant docs and answer.

Reference implementation (verified against Spring AI 1.0.0 GA):

```java
package org.example.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    @Value("classpath:materials/*")
    private Resource[] materialResources;

    public RagService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    public int ingestMaterials() {
        List<Document> all = new ArrayList<>();
        for (Resource res : materialResources) {
            String name = res.getFilename();
            if (name == null || name.startsWith("README")) {
                continue;
            }
            DocumentReader reader = new TikaDocumentReader(res);
            List<Document> docs = reader.get();
            all.addAll(splitter.apply(docs));
        }
        if (!all.isEmpty()) {
            vectorStore.add(all);
        }
        return all.size();
    }

    public String query(String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(4).build())
                        .build())
                .call()
                .content();
    }
}
```

### Step 6: Create REST Controller

Create `org.example.ai.controller.RagController.java`:

```java
package org.example.ai.controller;

import org.example.ai.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        int count = ragService.ingestMaterials();
        return Map.of("status", "ok", "chunksIngested", count);
    }

    @GetMapping("/query")
    public Map<String, String> query(@RequestParam String question) {
        String answer = ragService.query(question);
        return Map.of("question", question, "answer", answer);
    }
}
```

### Step 7: Verify & Report

1. Run `mvn clean compile` to ensure compilation succeeds.
2. Tell the user to:
   - `ollama pull nomic-embed-text` (embedding model)
   - Drop documents into `src/main/resources/materials/`
   - Start the app, then `POST /rag/ingest` to load materials
   - `GET /rag/query?question=xxx` to query

## Conventions

- Follow existing package layout under `org.example.ai` (controller/service/model/config).
- Use constructor injection or `@Autowired` matching neighbor files.
- Do NOT add comments unless the user asks.
- Keep responses in the user's language (Chinese by default).
- If an API signature differs in Spring AI 1.0.0, inspect the actual JAR with `javap -classpath <jar> <class>` or check the BOM POM rather than guessing.