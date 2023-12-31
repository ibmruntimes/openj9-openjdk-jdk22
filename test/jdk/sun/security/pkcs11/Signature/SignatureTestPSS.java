/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.stream.IntStream;
import jtreg.SkippedException;

/**
 * @test
 * @bug 8080462 8226651 8242332
 * @summary Generate a RSASSA-PSS signature and verify it using PKCS11 provider
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main SignatureTestPSS
 */
public class SignatureTestPSS extends PKCS11Test {

    private static final String SIGALG = "RSASSA-PSS";

    private static final int[] KEYSIZES = { 2048, 3072 };
    private static final String[] DIGESTS = {
            "SHA-224", "SHA-256", "SHA-384" , "SHA-512",
            "SHA3-224", "SHA3-256", "SHA3-384" , "SHA3-512",
    };
    private static final byte[] DATA = generateData(100);

    /**
     * How much times signature updated.
     */
    private static final int UPDATE_TIMES_FIFTY = 50;

    /**
     * How much times signature initial updated.
     */
    private static final int UPDATE_TIMES_HUNDRED = 100;

    private static boolean skipTest = true;

    public static void main(String[] args) throws Exception {
        main(new SignatureTestPSS(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        if (!PSSUtil.isSignatureSupported(p)) {
            throw new SkippedException("Skip due to no support for " + SIGALG);
        }

        for (int kSize : KEYSIZES) {
            System.out.println("[KEYSIZE = " + kSize + "]");
            KeyPair kp = PSSUtil.generateKeys(p, kSize);
            PrivateKey privKey = kp.getPrivate();
            PublicKey pubKey = kp.getPublic();
            for (String hash : DIGESTS) {
                for (String mgfHash : DIGESTS) {
                    System.out.println("    [Hash  = " + hash +
                            ", MGF1 Hash = " + mgfHash + "]");
                    PSSUtil.AlgoSupport s =
                            PSSUtil.isHashSupported(p, hash, mgfHash);
                    if (s == PSSUtil.AlgoSupport.NO) {
                        System.out.println("    => Skip; no support");
                        continue;
                    }
                    checkSignature(p, DATA, pubKey, privKey, hash, mgfHash, s);
                }
            };
        }

        // start testing below
        if (skipTest) {
            throw new SkippedException("Test Skipped");
        }
    }

    private static void checkSignature(Provider p, byte[] data, PublicKey pub,
            PrivateKey priv, String hash, String mgfHash, PSSUtil.AlgoSupport s)
            throws NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, NoSuchProviderException,
            InvalidAlgorithmParameterException {

        // only test RSASSA-PSS signature against the supplied hash/mgfHash
        // if they are supported; otherwise PKCS11 library will throw
        // CKR_MECHANISM_PARAM_INVALID at Signature.initXXX calls
        Signature sig = Signature.getInstance(SIGALG, p);
        AlgorithmParameterSpec params = new PSSParameterSpec(
                hash, "MGF1", new MGF1ParameterSpec(mgfHash), 0, 1);
        sig.initSign(priv);

        try {
            sig.setParameter(params);
        } catch (InvalidAlgorithmParameterException iape) {
            if (s == PSSUtil.AlgoSupport.MAYBE) {
                // confirmed to be unsupported; skip the rest of the test
                System.out.println("    => Skip; no PSS support");
                return;
            } else {
                throw new RuntimeException("Unexpected Exception", iape);
            }
        }
        // start testing below
        skipTest = false;

        for (int i = 0; i < UPDATE_TIMES_HUNDRED; i++) {
            sig.update(data);
        }
        byte[] signedData = sig.sign();

        // Make sure signature verifies with original data
        // do we need to call sig.setParameter(params) again?
        sig.initVerify(pub);
        for (int i = 0; i < UPDATE_TIMES_HUNDRED; i++) {
            sig.update(data);
        }
        if (!sig.verify(signedData)) {
            throw new RuntimeException("Failed to verify signature");
        }

        // Make sure signature does NOT verify when the original data
        // has changed
        sig.initVerify(pub);
        for (int i = 0; i < UPDATE_TIMES_FIFTY; i++) {
            sig.update(data);
        }

        if (sig.verify(signedData)) {
            throw new RuntimeException("Failed to detect bad signature");
        }
        System.out.println("    => Passed");
    }
}
