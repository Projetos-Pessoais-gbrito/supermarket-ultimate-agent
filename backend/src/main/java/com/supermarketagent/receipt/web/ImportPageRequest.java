package com.supermarketagent.receipt.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param accessKey the key the user asked SEFAZ for
 * @param html      the SEFAZ page shown after the captcha (a receipt page is ~20-60 KB)
 */
public record ImportPageRequest(@NotBlank @Size(max = 60) String accessKey,
                                @NotBlank @Size(max = 2_000_000) String html) {
}
