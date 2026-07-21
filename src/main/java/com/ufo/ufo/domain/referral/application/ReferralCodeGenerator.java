package com.ufo.ufo.domain.referral.application;

import com.ufo.ufo.domain.referral.ReferralCodeProperties;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReferralCodeGenerator {

    private static final String REFERRAL_CODE_PREFIX = "UFO";
    private static final String BASE62_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int HASH_LENGTH = 6;
    private static final BigInteger BASE = BigInteger.valueOf(BASE62_CHARACTERS.length());
    private static final BigInteger CODE_SPACE = BASE.pow(HASH_LENGTH);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ReferralCodeProperties properties;

    public String generate(Long userId, int nonce) {
        if (userId == null || userId <= 0 || nonce < 0) {
            throw new ReferralCodeGenerationException();
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    properties.requiredHmacSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKey);
            byte[] digest = mac.doFinal((userId + ":" + nonce).getBytes(StandardCharsets.UTF_8));
            return REFERRAL_CODE_PREFIX + encodeBase62(new BigInteger(1, digest).mod(CODE_SPACE));
        } catch (GeneralSecurityException exception) {
            throw new ReferralCodeGenerationException();
        }
    }

    private String encodeBase62(BigInteger value) {
        char[] encoded = new char[HASH_LENGTH];
        for (int index = HASH_LENGTH - 1; index >= 0; index--) {
            BigInteger[] quotientAndRemainder = value.divideAndRemainder(BASE);
            encoded[index] = BASE62_CHARACTERS.charAt(quotientAndRemainder[1].intValue());
            value = quotientAndRemainder[0];
        }
        return new String(encoded);
    }
}
