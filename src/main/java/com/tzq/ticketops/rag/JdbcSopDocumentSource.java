package com.tzq.ticketops.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class JdbcSopDocumentSource implements SopDocumentSource {

    private final JdbcTemplate jdbcTemplate;

    JdbcSopDocumentSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SopDocument> load() {
        List<SopDocument> documents = jdbcTemplate.query(
                """
                        select id, title, source, content
                        from sop_document
                        order by id
                        """,
                (rs, rowNum) -> new SopDocument(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("source"),
                        rs.getString("content")
                )
        );
        return List.copyOf(documents);
    }
}
