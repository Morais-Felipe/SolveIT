package com.solveit.repository;

import com.solveit.entity.Chamado;
import com.solveit.enums.StatusChamado;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChamadoRepository
        extends JpaRepository<Chamado, Long>, JpaSpecificationExecutor<Chamado> {

    /**
     * Carrega as relações exibidas na lista junto com a página, evitando uma consulta
     * extra por chamado. São todas to-one, então a paginação continua no SQL.
     */
    @Override
    @EntityGraph(attributePaths = {"solicitante", "atendente", "categoria"})
    Page<Chamado> findAll(Specification<Chamado> spec, Pageable pageable);

    /** Chamados de um atendente num status, em ordem de id (a ordem dos locks). */
    List<Chamado> findByAtendenteIdAndStatusOrderByIdAsc(Long atendenteId, StatusChamado status);
}
