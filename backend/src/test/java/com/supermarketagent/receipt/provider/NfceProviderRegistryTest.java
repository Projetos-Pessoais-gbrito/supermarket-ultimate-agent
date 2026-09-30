package com.supermarketagent.receipt.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermarketagent.receipt.domain.AccessKey;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NfceProviderRegistryTest {

    private static final AccessKey SP_KEY = new AccessKey("35260111222333000181650010000123451123456788");

    private final NfceProvider sp = new StubProvider("35");
    private final NfceProvider rj = new StubProvider("33");
    private final NfceProviderRegistry registry = new NfceProviderRegistry(List.of(sp, rj));

    @Test
    void selectsProviderByStateCodeOfTheKey() {
        assertThat(registry.providerFor(SP_KEY)).isSameAs(sp);
    }

    @Test
    void rejectsStatesWithoutProvider() {
        NfceProviderRegistry onlyRj = new NfceProviderRegistry(List.of(rj));

        assertThatThrownBy(() -> onlyRj.providerFor(SP_KEY))
                .isInstanceOf(UnsupportedStateException.class)
                .hasMessageContaining("35");
    }

    @Test
    void failsFastOnDuplicateProvidersForTheSameState() {
        assertThatThrownBy(() -> new NfceProviderRegistry(List.of(sp, new StubProvider("35"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private record StubProvider(String stateCode) implements NfceProvider {

        @Override
        public ZoneId timeZone() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Override
        public FetchedReceipt fetch(AccessKey accessKey, String qrCodeUrl) {
            throw new UnsupportedOperationException();
        }
    }
}
