package com.tzq.ticketops.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SopVectorRetrievalTest {

    private final SopSearchService searchService = SopSearchService.createOffline(0.30);

    @Test
    void retrievesAccountLockCitationForChineseAndEnglishQueries() {
        SopSearchResult chinese = searchService.search("我登录 OA 时提示账号已锁定，无法进入系统。");
        SopSearchResult english = searchService.search("My OA account is locked and I cannot sign in.");

        assertAcceptedAccountLock(chinese);
        assertAcceptedAccountLock(english);
    }

    @Test
    void retrievesPermissionAndMfaDocumentsByVectorSimilarity() {
        SopSearchResult permission = searchService.search("CRM says access denied; I need to request permission.");
        SopSearchResult mfa = searchService.search("VPN authenticator verification code does not work.");

        assertThat(permission.status()).isEqualTo(SopSearchStatus.ACCEPTED);
        assertThat(permission.reference()).get().extracting(reference -> reference.id())
                .isEqualTo("SOP-PERMISSION-REQUEST");
        assertThat(mfa.status()).isEqualTo(SopSearchStatus.ACCEPTED);
        assertThat(mfa.reference()).get().extracting(reference -> reference.id())
                .isEqualTo("SOP-MFA-ISSUE");
    }

    @Test
    void refusesUnrelatedQuestionInsteadOfReturningFirstDocument() {
        SopSearchResult result = searchService.search("How do I bake a chocolate birthday cake?");

        assertThat(result.status()).isEqualTo(SopSearchStatus.LOW_SIMILARITY);
        assertThat(result.reference()).isEmpty();
        assertThat(result.bestScore()).isLessThan(result.threshold());
        assertThat(result.embeddingProvider()).isEqualTo("offline");
    }

    private void assertAcceptedAccountLock(SopSearchResult result) {
        assertThat(result.status()).isEqualTo(SopSearchStatus.ACCEPTED);
        assertThat(result.reference()).get().satisfies(reference -> {
            assertThat(reference.id()).isEqualTo("SOP-ACCOUNT-LOCKED");
            assertThat(reference.source()).isEqualTo("mock-sop/account-locked.md");
            assertThat(reference.chunkId()).isEqualTo("SOP-ACCOUNT-LOCKED#chunk-0");
            assertThat(reference.chunkIndex()).isZero();
            assertThat(reference.totalChunks()).isEqualTo(1);
            assertThat(reference.similarity()).isGreaterThanOrEqualTo(result.threshold());
        });
    }
}
