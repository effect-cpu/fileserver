package com.milktea.fileserver;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.Signature;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public final class SelfSignedCert {

    private static final byte[] OID_SHA256_RSA = {
            0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B
    };
    private static final byte[] OID_CN = {0x06, 0x03, 0x55, 0x04, 0x03};
    private static final byte[] ALG_NULL = {0x05, 0x00};

    private SelfSignedCert() {
    }


    public static byte[] generate(KeyPair pair, byte[] publicKeyDer, String commonName, int days)
            throws Exception {
        byte[] tbs = tbsCertificate(publicKeyDer, commonName, days);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(tbs);
        byte[] signature = signer.sign();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tbs);
        body.write(sigAlgId());
        body.write(der(0x03, concat(new byte[]{0x00}, signature)));
        out.write(der(0x30, body.toByteArray()));
        return out.toByteArray();
    }

    private static byte[] tbsCertificate(byte[] publicKeyDer, String commonName, int days)
            throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();


        ByteArrayOutputStream ver = new ByteArrayOutputStream();
        ver.write(der(0x02, new byte[]{0x02}));
        body.write(der(0xA0, ver.toByteArray()));


        byte[] serial = new byte[8];
        new java.security.SecureRandom().nextBytes(serial);
        serial[0] &= 0x7F;
        if (serial[0] == 0) serial[0] = 1;
        body.write(der(0x02, serial));


        body.write(sigAlgId());


        byte[] atv = der(0x30, concat(OID_CN, der(0x0C, commonName.getBytes("UTF-8"))));
        byte[] rdn = der(0x31, atv);
        byte[] name = der(0x30, rdn);
        body.write(name);


        long now = System.currentTimeMillis();
        long notBefore = now - 86400_000L;
        long notAfter = now + (long) days * 86400_000L;
        ByteArrayOutputStream validity = new ByteArrayOutputStream();
        validity.write(der(0x17, utcTime(notBefore)));
        validity.write(der(0x17, utcTime(notAfter)));
        body.write(der(0x30, validity.toByteArray()));


        body.write(name);


        body.write(publicKeyDer);

        return der(0x30, body.toByteArray());
    }


    private static byte[] sigAlgId() throws Exception {
        return der(0x30, concat(OID_SHA256_RSA, ALG_NULL));
    }

    private static byte[] utcTime(long millis) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis)).getBytes("US-ASCII");
    }


    private static byte[] der(int tag, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        out.write(length(content.length));
        out.write(content);
        return out.toByteArray();
    }

    private static byte[] length(int n) {
        if (n < 0x80) return new byte[]{(byte) n};
        if (n <= 0xFF) return new byte[]{(byte) 0x81, (byte) n};
        if (n <= 0xFFFF) return new byte[]{(byte) 0x82, (byte) (n >> 8), (byte) n};
        return new byte[]{(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    private static byte[] concat(byte[] a, byte[] b) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(a);
        out.write(b);
        return out.toByteArray();
    }
}
