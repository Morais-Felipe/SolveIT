package com.solveit.repository;

import com.solveit.entity.Comentario;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    /** Ordem cronológica feita no SQL; o id desempata comentários do mesmo instante. */
    @EntityGraph(attributePaths = "autor")
    List<Comentario> findByChamadoIdOrderByCriadoEmAscIdAsc(Long chamadoId);
}
