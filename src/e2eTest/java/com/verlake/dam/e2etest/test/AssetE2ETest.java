package com.verlake.dam.e2etest.test;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Asset/Owner/Audit Trail Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AssetE2ETest extends BaseE2ETest {


    @BeforeAll
    void setupTestData() {
        super.baseSetUp();
    }

    @Test
    @Order(1)
    @DisplayName("Create an Asset")
    void createAnAsset() throws InterruptedException {
        createAsset();
    }

    @Test
    @Order(2)
    @DisplayName("Assign owner to created asset and set ownership")
    void createAndAssignAssetOwner() throws InterruptedException {
        createAndSetAssetOwner();
    }

    @Test
    @Order(3)
    @DisplayName("Log in as Asset Owner, create and relinquish Credential")
    void createAndRelinquishCredential() throws InterruptedException {
        loginAssetOwnerAndCreateCredential();
        
        //Relinquish Credential
        assetCredentialsPage.relinquishCredential(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName);
        dashboardPage.logout();
    }

    @Test
    @Order(4)
    @DisplayName("Verify audit trail as auditor")
    void auditVerification() throws InterruptedException {
        loginAsAuditorAndNavigateToAuditTrail(auditorUsername, auditorPassword);
        assertThat(auditPage.verifyAuditEntry("CREATE", assetName)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", assetOwnerUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", "ASSET_CREDENTIAL")).isTrue();
        assertThat(auditPage.verifyAuditEntry("UPDATE", assetCredentialUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("DELETE", assetCredentialUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", "RELINQUISH_ASSET_CREDENTIAL")).isTrue();
        assertThat(auditPage.verifyAuditEntry("UPDATE", "RELINQUISH_ASSET_CREDENTIAL")).isTrue();
    }
}
