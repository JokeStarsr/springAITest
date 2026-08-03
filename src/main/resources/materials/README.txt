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