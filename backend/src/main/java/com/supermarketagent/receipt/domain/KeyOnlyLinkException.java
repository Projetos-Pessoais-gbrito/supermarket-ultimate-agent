package com.supermarketagent.receipt.domain;

/** The link only has the access key; SEFAZ asks for a captcha on those, so the QR code is needed. */
public class KeyOnlyLinkException extends RuntimeException {

    public KeyOnlyLinkException() {
        super("This link only has the access key; SEFAZ requires a captcha for it. Scan the receipt QR code instead.");
    }
}
