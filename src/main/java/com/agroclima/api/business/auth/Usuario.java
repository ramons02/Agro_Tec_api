package com.agroclima.api.business.auth;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class Usuario extends BaseModel<UUID> {

    @Id
    private UUID id;

    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    /** null apenas enquanto a conta esta PENDENTE (sem senha utilizavel). */
    @Column(name = "senha_hash")
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "papel_usuario")
    private Papel papel;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /** null = sem Telegram vinculado (FR-005, feature 017) -- nunca dispara tentativa de envio. */
    @Column(name = "telegram_chat_id")
    private String telegramChatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusConta status = StatusConta.ATIVA;

    @Column(name = "precisa_trocar_senha", nullable = false)
    private boolean precisaTrocarSenha;

    @Column(name = "senha_temporaria_expira_em")
    private Instant senhaTemporariaExpiraEm;

    /** Incrementa quando as sessoes abertas devem cair; o token carrega o valor (claim ver). */
    @Column(name = "versao_sessao", nullable = false)
    private int versaoSessao;

    @Column(name = "motivo_suspensao")
    private String motivoSuspensao;

    @Column(name = "suspensa_em")
    private Instant suspensaEm;

    /** Quando o produtor ou agronomo pediu nova senha na tela de login; nulo = sem pedido pendente. */
    @Column(name = "senha_esquecida_em")
    private Instant senhaEsquecidaEm;

    protected Usuario() {}

    public Usuario(String nome, String email, String senhaHash, Papel papel) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.criadoEm = Instant.now();
    }

    /** Cadastro aberto: conta sem senha, aguardando a confirmacao do gestor. */
    public static Usuario pendente(String nome, String email, Papel papel, Instant agora) {
        Usuario usuario = new Usuario(nome, email, null, papel);
        usuario.status = StatusConta.PENDENTE;
        usuario.criadoEm = agora;
        return usuario;
    }

    /** Conta criada direto pelo gestor: ja ATIVA, com senha temporaria e troca obrigatoria. */
    public static Usuario criadaPeloGestor(String nome, String email, Papel papel, String senhaTemporariaHash,
            Instant agora, Instant senhaExpiraEm) {
        Usuario usuario = new Usuario(nome, email, senhaTemporariaHash, papel);
        usuario.criadoEm = agora;
        usuario.precisaTrocarSenha = true;
        usuario.senhaTemporariaExpiraEm = senhaExpiraEm;
        return usuario;
    }

    /** Recuperacao de senha por e-mail (HU-13): a pessoa escolheu a senha, entao nao ha troca obrigatoria. */
    public void atualizarSenha(String novaSenhaHash) {
        this.senhaHash = novaSenhaHash;
        this.precisaTrocarSenha = false;
        this.senhaTemporariaExpiraEm = null;
        this.versaoSessao++;
    }

    /** PENDENTE -> ATIVA, com senha temporaria e troca obrigatoria. */
    public void confirmar(String senhaTemporariaHash, Instant senhaExpiraEm) {
        this.status = StatusConta.ATIVA;
        definirSenhaTemporaria(senhaTemporariaHash, senhaExpiraEm);
    }

    /** Nova senha temporaria: a anterior e as sessoes abertas deixam de valer. */
    public void definirSenhaTemporaria(String senhaTemporariaHash, Instant senhaExpiraEm) {
        this.senhaEsquecidaEm = null;
        this.senhaHash = senhaTemporariaHash;
        this.precisaTrocarSenha = true;
        this.senhaTemporariaExpiraEm = senhaExpiraEm;
        this.versaoSessao++;
    }

    /** A pessoa definiu a propria senha (primeiro acesso ou troca voluntaria). */
    public void trocarSenha(String novaSenhaHash) {
        this.senhaEsquecidaEm = null;
        this.senhaHash = novaSenhaHash;
        this.precisaTrocarSenha = false;
        this.senhaTemporariaExpiraEm = null;
        this.versaoSessao++;
    }

    /** Registra o pedido de nova senha; um pedido repetido nao muda a data do primeiro. */
    public void pedirNovaSenha(Instant agora) {
        if (this.senhaEsquecidaEm == null) {
            this.senhaEsquecidaEm = agora;
        }
    }

    /** A pessoa lembrou da senha e entrou: o pedido de nova senha deixa de ser pendencia. */
    public void limparPedidoDeNovaSenha() {
        this.senhaEsquecidaEm = null;
    }

    public void suspender(String motivo, Instant agora) {
        this.status = StatusConta.SUSPENSA;
        this.motivoSuspensao = motivo;
        this.suspensaEm = agora;
    }

    public void reativar() {
        this.status = StatusConta.ATIVA;
        this.motivoSuspensao = null;
        this.suspensaEm = null;
    }

    /** Trocar o e-mail derruba as sessoes abertas (a versao de sessao muda). */
    public void atualizarNomeEmail(String novoNome, String novoEmail) {
        this.nome = novoNome;
        if (!this.email.equalsIgnoreCase(novoEmail)) {
            this.versaoSessao++;
        }
        this.email = novoEmail;
    }

    public void vincularTelegram(String chatId) {
        this.telegramChatId = chatId;
    }

    public void desvincularTelegram() {
        this.telegramChatId = null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public StatusConta getStatus() {
        return status;
    }

    public boolean isPrecisaTrocarSenha() {
        return precisaTrocarSenha;
    }

    public Instant getSenhaTemporariaExpiraEm() {
        return senhaTemporariaExpiraEm;
    }

    public int getVersaoSessao() {
        return versaoSessao;
    }

    public String getMotivoSuspensao() {
        return motivoSuspensao;
    }

    public Instant getSenhaEsquecidaEm() {
        return senhaEsquecidaEm;
    }

    public Instant getSuspensaEm() {
        return suspensaEm;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }
}
