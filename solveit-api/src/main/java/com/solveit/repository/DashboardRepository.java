package com.solveit.repository;

import com.solveit.dto.dashboard.DashboardDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Agregados do dashboard calculados no MySQL com COUNT + GROUP BY. Toda consulta parte
 * do mesmo predicado de visibilidade da lista de chamados
 * ({@link ChamadoSpecifications#visivelPara(Usuario)}), por isso os números batem com
 * {@code GET /api/chamados} usando os filtros equivalentes. Nenhum Chamado é carregado.
 */
@Repository
public class DashboardRepository {

    private final EntityManager entityManager;

    public DashboardRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Map<StatusChamado, Long> contarPorStatus(Usuario usuario) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Chamado> chamado = query.from(Chamado.class);
        Expression<StatusChamado> status = chamado.get("status");
        Expression<Long> total = cb.count(chamado);

        query.multiselect(status, total)
                .where(visivel(usuario, chamado, query, cb))
                .groupBy(status);

        Map<StatusChamado, Long> contagem = new EnumMap<>(StatusChamado.class);
        for (StatusChamado valor : StatusChamado.values()) {
            contagem.put(valor, 0L);
        }
        for (Tuple linha : entityManager.createQuery(query).getResultList()) {
            contagem.put(linha.get(status), linha.get(total));
        }
        return contagem;
    }

    /** Chamados ABERTO por prioridade; todas as prioridades aparecem, inclusive zeradas. */
    public Map<Prioridade, Long> abertosPorPrioridade(Usuario usuario) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Chamado> chamado = query.from(Chamado.class);
        Expression<Prioridade> prioridade = chamado.get("prioridade");
        Expression<Long> total = cb.count(chamado);

        query.multiselect(prioridade, total)
                .where(
                        visivel(usuario, chamado, query, cb),
                        cb.equal(chamado.get("status"), StatusChamado.ABERTO))
                .groupBy(prioridade);

        Map<Prioridade, Long> contagem = new EnumMap<>(Prioridade.class);
        for (Prioridade valor : Prioridade.values()) {
            contagem.put(valor, 0L);
        }
        for (Tuple linha : entityManager.createQuery(query).getResultList()) {
            contagem.put(linha.get(prioridade), linha.get(total));
        }
        return contagem;
    }

    /** Todos os chamados visíveis por categoria, de qualquer status (decisão D-4). */
    public List<DashboardDtos.PorCategoria> porCategoria(Usuario usuario) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Chamado> chamado = query.from(Chamado.class);
        Join<Chamado, Categoria> categoria = chamado.join("categoria");
        Expression<Long> id = categoria.get("id");
        Expression<String> nome = categoria.get("nome");
        Expression<Long> total = cb.count(chamado);

        query.multiselect(id, nome, total)
                .where(visivel(usuario, chamado, query, cb))
                .groupBy(id, nome)
                .orderBy(cb.desc(total), cb.asc(nome));

        return entityManager.createQuery(query).getResultList().stream()
                .map(linha -> new DashboardDtos.PorCategoria(
                        linha.get(id), linha.get(nome), linha.get(total)))
                .toList();
    }

    /** Carga ativa: chamados EM_ANDAMENTO visíveis por atendente responsável (D-2). */
    public List<DashboardDtos.CargaAtendente> cargaPorAtendente(Usuario usuario) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Chamado> chamado = query.from(Chamado.class);
        Join<Chamado, Usuario> atendente = chamado.join("atendente");
        Expression<Long> id = atendente.get("id");
        Expression<String> nome = atendente.get("nome");
        Expression<Long> total = cb.count(chamado);

        query.multiselect(id, nome, total)
                .where(
                        visivel(usuario, chamado, query, cb),
                        cb.equal(chamado.get("status"), StatusChamado.EM_ANDAMENTO))
                .groupBy(id, nome)
                .orderBy(cb.desc(total), cb.asc(nome));

        return entityManager.createQuery(query).getResultList().stream()
                .map(linha -> new DashboardDtos.CargaAtendente(
                        linha.get(id), linha.get(nome), linha.get(total)))
                .toList();
    }

    private static Predicate visivel(
            Usuario usuario,
            Root<Chamado> chamado,
            CriteriaQuery<?> query,
            CriteriaBuilder cb) {
        return ChamadoSpecifications.visivelPara(usuario).toPredicate(chamado, query, cb);
    }
}
