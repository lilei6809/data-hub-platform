package io.datahub.platform.iamprovisioning.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityExtensionPlaceholderTest {

    @Test
    void should_exposeMvpDefaults_when_usingIdentityAndRealmEnums() {
        assertThat(IdentityMode.DEFAULT).isEqualTo(IdentityMode.LOCAL_ONLY);
        assertThat(RealmStrategy.DEFAULT).isEqualTo(RealmStrategy.SHARED_REALM);
    }

    @Test
    void should_keepFutureExtensionEnumValuesAvailable_when_mvpUsesDefaults() {
        assertThat(IdentityMode.values())
                .containsExactly(IdentityMode.LOCAL_ONLY, IdentityMode.BROKERED_IDP);
        assertThat(RealmStrategy.values())
                .containsExactly(RealmStrategy.SHARED_REALM, RealmStrategy.DEDICATED_REALM);
    }

    @Test
    void should_treatPlaceholderConfigsAsValueObjects_when_constructed() {
        assertThat(new IdentityProviderConfig()).isEqualTo(new IdentityProviderConfig());
        assertThat(new PolicyConfig()).isEqualTo(new PolicyConfig());
    }
}
