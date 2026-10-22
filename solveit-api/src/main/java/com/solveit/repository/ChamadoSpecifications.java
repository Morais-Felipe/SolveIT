package com.solveit.repository;

import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import org.springframework.data.jpa.domain.Specification;

/**
 * Peças do WHERE de consultas de chamado. {@link #visivelPara(Usuario)} traduz para SQL
 * a mesma regra de {@code ChamadoAuthorizationService.podeAcessar}; os filtros opcionais
 * são sempre combinados com ela por AND, então nunca ampliam o escopo.
 */
public final class ChamadoSpecifications {

    private ChamadoSpecifications() {
    }

    public static Specification<Chamado> visivelPara(Usuario usuario) {
        return (root, query, cb) -> {
            if (!Boolean.TRUE.equals(usuario.getAtivo())) {
                return cb.disjunction();
            }
            return switch (usuario.getPerfil()) {
                case ADMIN -> cb.conjunction();
                case SOLICITANTE -> cb.equal(root.get("solicitante").get("id"), usuario.getId());
                case ATENDENTE -> cb.or(
                        cb.and(
                                cb.equal(root.get("status"), StatusChamado.ABERTO),
                                cb.isNull(root.get("atendente"))),
                        cb.equal(root.get("atendente").get("id"), usuario.getId()));
            };
        };
    }

    public static Specification<Chamado> comStatus(StatusChamado status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Chamado> comPrioridade(Prioridade prioridade) {
        return prioridade == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("prioridade"), prioridade);
    }

    public static Specification<Chamado> daCategoria(Long categoriaId) {
        return categoriaId == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId);
    }

    public static Specification<Chamado> doAtendente(Long atendenteId) {
        return atendenteId == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("atendente").get("id"), atendenteId);
    }

    public static Specification<Chamado> doSolicitante(Long solicitanteId) {
        return solicitanteId == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("solicitante").get("id"), solicitanteId);
    }
}
