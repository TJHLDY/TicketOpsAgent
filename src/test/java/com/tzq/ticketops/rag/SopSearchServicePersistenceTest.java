package com.tzq.ticketops.rag;

import com.tzq.ticketops.agent.SopReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SopSearchServicePersistenceTest {

    @Autowired
    SopSearchService sopSearchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSopDocuments() {
        jdbcTemplate.update("delete from sop_document");
    }

    @Test
    void findsAccountLockedSopFromDatabase() {
        jdbcTemplate.update(
                """
                        insert into sop_document(id, title, source, content)
                        values (?, ?, ?, ?)
                        """,
                "SOP-ACCOUNT-LOCKED",
                "DB 账号锁定 SOP",
                "db-sop/account-locked.md",
                "账号锁定时先查询账号状态，再生成待人工确认的解锁动作。"
        );

        SopReference reference = sopSearchService.findBest("OA 登录失败，提示账号已锁定。");

        assertThat(reference.id()).isEqualTo("SOP-ACCOUNT-LOCKED");
        assertThat(reference.title()).isEqualTo("DB 账号锁定 SOP");
        assertThat(reference.source()).isEqualTo("db-sop/account-locked.md");
        assertThat(reference.similarity()).isEqualTo(0.92);
    }
}
