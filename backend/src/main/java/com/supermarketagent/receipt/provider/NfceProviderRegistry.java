package com.supermarketagent.receipt.provider;

import com.supermarketagent.receipt.domain.AccessKey;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Picks the {@link NfceProvider} for a receipt based on the state code in its access key. */
@Component
public class NfceProviderRegistry {

    private final Map<String, NfceProvider> providersByState;

    public NfceProviderRegistry(List<NfceProvider> providers) {
        this.providersByState = providers.stream()
                .collect(Collectors.toUnmodifiableMap(NfceProvider::stateCode, Function.identity()));
    }

    public NfceProvider providerFor(AccessKey accessKey) {
        NfceProvider provider = providersByState.get(accessKey.stateCode());
        if (provider == null) {
            throw new UnsupportedStateException(accessKey.stateCode());
        }
        return provider;
    }
}
