package com.agroclima.api.business.push;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Par de chaves VAPID (P-256) que identifica esta API perante o serviço de push. Vem das variáveis
 * VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY se estiverem preenchidas; senão a API gera um par na primeira
 * necessidade e o guarda em `config_push`, para sobreviver a reinícios e valer em todas as instâncias
 * (trocar a chave invalida todas as assinaturas existentes).
 */
@Component
public class ChavesVapid {

    public record Par(String publica, String privada) {}

    private final JdbcTemplate jdbc;
    private final String publicaConfigurada;
    private final String privadaConfigurada;
    private volatile Par par;

    public ChavesVapid(
            JdbcTemplate jdbc,
            @Value("${app.push.vapid-public-key:}") String publicaConfigurada,
            @Value("${app.push.vapid-private-key:}") String privadaConfigurada) {
        this.jdbc = jdbc;
        this.publicaConfigurada = publicaConfigurada;
        this.privadaConfigurada = privadaConfigurada;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public Par obter() {
        Par atual = par;
        if (atual == null) {
            synchronized (this) {
                if (par == null) {
                    par = carregar();
                }
                atual = par;
            }
        }
        return atual;
    }

    private Par carregar() {
        if (!publicaConfigurada.isBlank() && !privadaConfigurada.isBlank()) {
            return new Par(publicaConfigurada, privadaConfigurada);
        }
        Par guardado = lerDoBanco();
        if (guardado != null) {
            return guardado;
        }
        Par novo = gerar();
        // ON CONFLICT: duas instancias subindo juntas nao podem criar chaves diferentes.
        jdbc.update("INSERT INTO config_push (id, chave_publica, chave_privada, criado_em) VALUES (1, ?, ?, ?) "
                + "ON CONFLICT (id) DO NOTHING", novo.publica(), novo.privada(), OffsetDateTime.now());
        Par vencedor = lerDoBanco();
        return vencedor != null ? vencedor : novo;
    }

    private Par lerDoBanco() {
        List<Par> linhas = jdbc.query("SELECT chave_publica, chave_privada FROM config_push WHERE id = 1",
                (rs, n) -> new Par(rs.getString(1), rs.getString(2)));
        return linhas.isEmpty() ? null : linhas.get(0);
    }

    static Par gerar() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            ECParameterSpec curva = ECNamedCurveTable.getParameterSpec("prime256v1");
            gerador.initialize(curva);
            KeyPair chaves = gerador.generateKeyPair();
            byte[] publica = ((ECPublicKey) chaves.getPublic()).getQ().getEncoded(false); // 65 bytes, 0x04 + X + Y
            BigInteger d = ((ECPrivateKey) chaves.getPrivate()).getD();
            return new Par(base64Url(publica), base64Url(paraTrintaEDoisBytes(d)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Não consegui gerar as chaves VAPID.", ex);
        }
    }

    private static byte[] paraTrintaEDoisBytes(BigInteger valor) {
        byte[] bytes = valor.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] saida = new byte[32];
        if (bytes.length > 32) { // byte de sinal sobrando
            System.arraycopy(bytes, bytes.length - 32, saida, 0, 32);
        } else {
            System.arraycopy(bytes, 0, saida, 32 - bytes.length, bytes.length);
        }
        return saida;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(bytes, bytes.length));
    }
}
