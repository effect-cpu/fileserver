package com.milktea.fileserver;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;


public final class TlsFactory {

    private static final String ALIAS = "localserver";

    private TlsFactory() {
    }

    public static SSLServerSocketFactory create() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        byte[] der = SelfSignedCert.generate(pair, pair.getPublic().getEncoded(), "本地文件服务器", 825);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
        cert.verify(pair.getPublic());

        KeyManager[] managers = new KeyManager[]{new ServerKeyManager(pair.getPrivate(), cert)};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(managers, null, null);
        return ctx.getServerSocketFactory();
    }


    private static final class ServerKeyManager extends X509ExtendedKeyManager {

        private final PrivateKey key;
        private final X509Certificate[] chain;

        ServerKeyManager(PrivateKey key, X509Certificate cert) {
            this.key = key;
            this.chain = new X509Certificate[]{cert};
        }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            return null;
        }

        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[]{ALIAS};
        }

        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return ALIAS;
        }

        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return ALIAS;
        }

        public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
            return null;
        }

        public X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? chain.clone() : null;
        }

        public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? key : null;
        }
    }
}
