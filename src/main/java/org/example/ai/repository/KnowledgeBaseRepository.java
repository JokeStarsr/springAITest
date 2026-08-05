package org.example.ai.repository;

import org.example.ai.model.KnowledgeBase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class KnowledgeBaseRepository {

    private final JdbcTemplate jdbc;

    public KnowledgeBaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    private void initTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS knowledge_bases (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL UNIQUE,
                description TEXT DEFAULT '',
                doc_count INTEGER DEFAULT 0,
                upload_dir VARCHAR(500) DEFAULT '',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    public List<KnowledgeBase> findAll() {
        return jdbc.query("SELECT * FROM knowledge_bases ORDER BY created_at DESC",
            (rs, row) -> new KnowledgeBase(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("doc_count"),
                rs.getTimestamp("created_at").toString(),
                rs.getTimestamp("updated_at").toString(),
                rs.getString("upload_dir")
            )
        );
    }

    public KnowledgeBase findById(long id) {
        List<KnowledgeBase> list = jdbc.query(
            "SELECT * FROM knowledge_bases WHERE id = ?",
            (rs, row) -> new KnowledgeBase(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("doc_count"),
                rs.getTimestamp("created_at").toString(),
                rs.getTimestamp("updated_at").toString(),
                rs.getString("upload_dir")
            ),
            id
        );
        return list.isEmpty() ? null : list.get(0);
    }

    public long create(String name, String description, String uploadDir) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO knowledge_bases (name, description, upload_dir) VALUES (?, ?, ?)",
                new String[]{"id"}
            );
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, uploadDir);
            return ps;
        }, holder);
        return holder.getKey().longValue();
    }

    public void updateDocCount(long id, int count) {
        jdbc.update("UPDATE knowledge_bases SET doc_count = ?, updated_at = ? WHERE id = ?",
            count, new Timestamp(System.currentTimeMillis()), id);
    }

    public void deleteById(long id) {
        jdbc.update("DELETE FROM knowledge_bases WHERE id = ?", id);
    }
}