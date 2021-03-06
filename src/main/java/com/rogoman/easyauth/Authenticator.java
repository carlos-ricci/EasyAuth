/*
 * Copyright 2015 Tomasz Rogozik
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.rogoman.easyauth;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.apache.commons.lang.StringUtils;

/**
 * A base class for all the possible implemented authenticators. Exposes a way to generate new secret keys as well as
 * an internal method for getting codes based on those keys.
 */
public abstract class Authenticator {
    private static final String CRYPTO_ALGORITHM = "HmacSHA1";

    private static final String RANDOM_NUMBER_GENERATOR_NAME = "SHA1PRNG";

    private static final SecureRandom RANDOM;   // Is Thread-Safe

    static {
        SecureRandom random = null;
        try {
            random = SecureRandom.getInstance(RANDOM_NUMBER_GENERATOR_NAME);
        } catch (final NoSuchAlgorithmException e) {
            random = new SecureRandom();
        } finally {
            RANDOM = random;
        }
    }

    private static final int KEY_LENGTH = 16;

    private static final String AVAILABLE_KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Generates a new secret key so a new user can use it.
     *
     * @return secret key
     */
    public static final String generateKey() {
        final char[] keyChars = new char[KEY_LENGTH];
        for (int i = 0; i < keyChars.length; i++) {
            keyChars[i] = AVAILABLE_KEY_CHARS.charAt(randomInt(AVAILABLE_KEY_CHARS.length()));
        }
        return new String(keyChars);
    }

    /**
     * Gets a new code.
     *
     * @param secret secret used for generating the code
     * @return generated code
     * @exception java.security.InvalidKeyException if the secret passed has an invalid format
     * @exception com.rogoman.easyauth.AuthenticatorException if there is another problem in computing the code value
     */
    public abstract String getCode(final String secret) throws AuthenticatorException, InvalidKeyException;

    /**
     * Checks if the provided code is valid for given secret key and user identifier.
     *
     * @param secret         secret used for generating the code
     * @param code           generated code
     * @param userIdentifier user identifier
     * @return true if the code is valid and should be accepted
     */
    public abstract boolean checkCode(final String secret, final String code, final String userIdentifier);

    /**
     * Generates a verification code based on the passed secret and a challenge value.
     *
     * @param secret         passed secret key
     * @param challengeValue passed challenge value
     * @return generated code
     * @exception java.security.InvalidKeyException if the secret passed has an invalid format
     * @exception com.rogoman.easyauth.AuthenticatorException if there is another problem in computing the code value
     */
    protected String getCodeInternal(final String secret, final long challengeValue) throws InvalidKeyException, AuthenticatorException {
        long chlg = challengeValue;

        byte[] challenge = ByteBuffer.allocate(8).putLong(challengeValue).array();

        byte[] key = Base32Encoding.toBytes(secret);
        for (int i = secret.length(); i < key.length; i++) {
            key[i] = 0;
        }

        byte[] hash;
        try {
            hash = HMAC.hmacDigest(challenge, key, CRYPTO_ALGORITHM);
        } catch (final NoSuchAlgorithmException e) {
            throw new AuthenticatorException("HmacSHA1 algorithm is not present in your JVM.", e);
        }

        int offset = hash[hash.length - 1] & 0xf;

        long truncatedHash = 0;
        for (int j = 0; j < 4; j++) {
            truncatedHash <<= 8;
            int absValue = (int) hash[offset + j] & 0xFF;
            truncatedHash |= absValue;
        }

        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= 1000000;

        String code = Long.toString(truncatedHash);
        return StringUtils.leftPad(code, 6, '0');
    }

    /**
     * Compares two strings.
     *
     * @param a first string to compare
     * @param b second string to compare
     * @return comparison result
     */
    protected boolean stringEquals(final String a, final String b) {
        int diff = a.length() ^ b.length();

        for (int i = 0; i < a.length() && i < b.length(); i++) {
            diff |= (int) a.charAt(i) ^ (int) b.charAt(i);
        }

        return diff == 0;
    }

    /**
     * Generates a random integer value.
     *
     * @param max maximum allowed value
     * @return random integer
     */
    protected static int randomInt(final int max) {
        return RANDOM.nextInt(max);
    }
}
