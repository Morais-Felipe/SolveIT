package com.solveit.entity;

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
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "historico_status")
public class HistoricoStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Chamado chamado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", nullable = true, length = 20)
    private StatusChamado statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 20)
    private StatusChamado statusNovo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alterado_por_id", nullable = false)
    private Usuario alteradoPor;

    @Column(name = "alterado_em", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime alteradoEm;

    @Column(name = "observacao", nullable = true, length = 500)
    private String observacao;

    protected HistoricoStatus() {
    }

    public HistoricoStatus(
            Chamado chamado,
            StatusChamado statusAnterior,
            StatusChamado statusNovo,
            Usuario alteradoPor,
            String observacao) {
        this.chamado = chamado;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.alteradoPor = alteradoPor;
        this.observacao = observacao;
    }

    /**
     * Registro de origem do chamado: sem estado anterior, no mesmo instante da criação.
     * Exige um chamado já persistido, para que {@code criadoEm} venha do servidor.
     */
    public static HistoricoStatus abertura(Chamado chamado, Usuario solicitante) {
        if (chamado.getCriadoEm() == null) {
            throw new IllegalStateException("Opening history requires a persisted ticket");
        }
        HistoricoStatus historico = new HistoricoStatus(
                chamado,
                null,
                StatusChamado.ABERTO,
                solicitante,
                null);
        historico.alteradoEm = chamado.getCriadoEm();
        return historico;
    }

    @PrePersist
    private void preencherDataDeAlteracao() {
        if (alteradoEm == null) {
            alteradoEm = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public Chamado getChamado() {
        return chamado;
    }

    public StatusChamado getStatusAnterior() {
        return statusAnterior;
    }

    public StatusChamado getStatusNovo() {
        return statusNovo;
    }

    public Usuario getAlteradoPor() {
        return alteradoPor;
    }

    public LocalDateTime getAlteradoEm() {
        return alteradoEm;
    }

    public String getObservacao() {
        return observacao;
    }
}
