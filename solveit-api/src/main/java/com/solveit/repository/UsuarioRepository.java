package com.solveit.repository;

import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByPerfilAndAtivoTrue(PerfilUsuario perfil);

    List<Usuario> findByPerfilAndAtivoTrueOrderByIdAsc(PerfilUsuario perfil);
}
