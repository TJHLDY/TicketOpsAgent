package com.tzq.ticketops.rag;

import com.tzq.ticketops.agent.SopReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

        SopSearchResult result = sopSearchService.search("OA 登录失败，提示账号已锁定。");

        assertThat(result.status()).isEqualTo(SopSearchStatus.ACCEPTED);
        assertThat(result.reference()).get().satisfies(reference -> {
            assertThat(reference.id()).isEqualTo("SOP-ACCOUNT-LOCKED");
            assertThat(reference.title()).isEqualTo("DB 账号锁定 SOP");
            assertThat(reference.source()).isEqualTo("db-sop/account-locked.md");
            assertThat(reference.similarity()).isGreaterThanOrEqualTo(result.threshold());
        });
    }

    @Test
    void returnsNoDocumentsInsteadOfSilentlyFallingBackWhenDatabaseIsEmpty() {
        SopSearchResult result = sopSearchService.search("OA account locked");

        assertThat(result.status()).isEqualTo(SopSearchStatus.NO_DOCUMENTS);
        assertThat(result.reference()).isEmpty();
        assertThat(result.bestScore()).isZero();
    }

    @Test
    void refreshesVectorIndexWhenDatabaseDocumentsChange() {
        jdbcTemplate.update(
                "insert into sop_document(id, title, source, content) values (?, ?, ?, ?)",
                "SOP-ACCOUNT-LOCKED",
                "旧账号锁定 SOP",
                "db-sop/old-account-locked.md",
                "账号锁定 account locked 无法登录 sign in"
        );
        assertThat(sopSearchService.search("account locked").reference()).get()
                .extracting(SopReference::source)
                .isEqualTo("db-sop/old-account-locked.md");

        jdbcTemplate.update("delete from sop_document");
        jdbcTemplate.update(
                "insert into sop_document(id, title, source, content) values (?, ?, ?, ?)",
                "SOP-PERMISSION-REQUEST",
                "新权限申请 SOP",
                "db-sop/new-permission.md",
                "权限申请 permission request access denied"
        );

        SopSearchResult refreshed = sopSearchService.search("CRM permission request access denied");

        assertThat(refreshed.status()).isEqualTo(SopSearchStatus.ACCEPTED);
        assertThat(refreshed.reference()).get().satisfies(reference -> {
            assertThat(reference.id()).isEqualTo("SOP-PERMISSION-REQUEST");
            assertThat(reference.source()).isEqualTo("db-sop/new-permission.md");
        });
        String staleResultId = sopSearchService.search("account locked")
                .reference()
                .map(SopReference::id)
                .orElse("");
        assertThat(staleResultId).isNotEqualTo("SOP-ACCOUNT-LOCKED");
    }

    @Test
    void retrievesLaterSectionWithChunkLevelCitation() {
        String relevantSection = "emergency account locked recovery requires identity verification before unlock review. ";
        String content = ("general retention policy handbook overview. ").repeat(60)
                + relevantSection.repeat(10);
        jdbcTemplate.update(
                "insert into sop_document(id, title, source, content) values (?, ?, ?, ?)",
                "SOP-LONG-ACCOUNT",
                "Long account operations handbook",
                "db-sop/long-account-operations.md",
                content
        );

        SopSearchResult result = sopSearchService.search(
                relevantSection
        );

        assertThat(result.status()).isEqualTo(SopSearchStatus.ACCEPTED);
        assertThat(result.reference()).get().satisfies(reference -> {
            assertThat(reference.id()).isEqualTo("SOP-LONG-ACCOUNT");
            assertThat(reference.chunkId()).startsWith("SOP-LONG-ACCOUNT#chunk-");
            assertThat(reference.chunkIndex()).isGreaterThan(0);
            assertThat(reference.totalChunks()).isGreaterThan(1);
            assertThat(reference.source()).isEqualTo("db-sop/long-account-operations.md");
        });
    }
}
