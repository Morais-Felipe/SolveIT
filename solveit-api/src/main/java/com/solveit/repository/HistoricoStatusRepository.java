package com.solveit.repository;

import com.solveit.entity.HistoricoStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {

    /** Linha do tempo completa, ordenada no SQL; o id desempata registros do mesmo instante. */
    @EntityGraph(attributePaths = "alteradoPor")
    List<HistoricoStatus> findByChamadoIdOrderByAlteradoEmAscIdAsc(Long chamadoId);
}
