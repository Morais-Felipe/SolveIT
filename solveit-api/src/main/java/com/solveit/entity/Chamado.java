package com.solveit.entity;

import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "chamado")
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusChamado status;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 10)
    private Prioridade prioridade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "atendente_id", nullable = true)
    private Usuario atendente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "criado_em", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime atualizadoEm;

    @Column(name = "resolvido_em", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime resolvidoEm;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Indica que atualizadoEm já foi definido por {@link #transicionar}; não é persistido. */
    @Transient
    private boolean atualizacaoDatada;

    protected Chamado() {
    }

    public Chamado(
            String titulo,
            String descricao,
            StatusChamado status,
            Prioridade prioridade,
            Usuario solicitante,
            Usuario atendente,
            Categoria categoria) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.status = status;
        this.prioridade = prioridade;
        this.solicitante = solicitante;
        this.atendente = atendente;
        this.categoria = categoria;
    }

    @PrePersist
    private void preencherDatasDeCriacao() {
        LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
    }

    /**
     * Aplica uma transição de status num instante definido pelo servidor. O mesmo instante
     * vira {@code atualizadoEm} e, na resolução, {@code resolvidoEm}; o histórico copia
     * {@code atualizadoEm}, então as três datas coincidem.
     */
    public void transicionar(StatusChamado destino, LocalDateTime instante) {
        this.status = destino;
        this.atualizadoEm = instante;
        this.atualizacaoDatada = true;
        if (destino == StatusChamado.RESOLVIDO) {
            this.resolvidoEm = instante;
        }
    }

    @PreUpdate
    private void preencherDataDeAtualizacao() {
        if (!atualizacaoDatada) {
            atualizadoEm = LocalDateTime.now(ZoneOffset.UTC);
        }
        atualizacaoDatada = false;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public StatusChamado getStatus() {
        return status;
    }

    public void setStatus(StatusChamado status) {
        this.status = status;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(Prioridade prioridade) {
        this.prioridade = prioridade;
    }

    public Usuario getSolicitante() {
        return solicitante;
    }

    public Usuario getAtendente() {
        return atendente;
    }

    public void setAtendente(Usuario atendente) {
        this.atendente = atendente;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public LocalDateTime getResolvidoEm() {
        return resolvidoEm;
    }

    public void setResolvidoEm(LocalDateTime resolvidoEm) {
        this.resolvidoEm = resolvidoEm;
    }

    public Long getVersion() {
        return version;
    }
}
