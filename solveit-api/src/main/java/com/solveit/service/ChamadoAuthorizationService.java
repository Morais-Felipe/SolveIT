package com.solveit.service;

import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.StatusChamado;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Regra única de leitura de chamados (D-01), sempre avaliada com o perfil e a relação
 * atuais do usuário recarregados do banco:
 * <ul>
 *   <li>ADMIN vê todos;</li>
 *   <li>SOLICITANTE vê somente os chamados que abriu;</li>
 *   <li>ATENDENTE vê a fila (ABERTO sem responsável) e os atribuídos a ele, inclusive
 *       encerrados.</li>
 * </ul>
 * Autoria de comentário ou relação passada não concede acesso.
 */
@Service
public class ChamadoAuthorizationService {

    public boolean podeAcessar(Usuario usuario, Chamado chamado) {
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            return false;
        }
        return switch (usuario.getPerfil()) {
            case ADMIN -> true;
            case SOLICITANTE -> mesmoUsuario(chamado.getSolicitante(), usuario);
            case ATENDENTE -> estaNaFila(chamado) || mesmoUsuario(chamado.getAtendente(), usuario);
        };
    }

    public void exigirAcesso(Usuario usuario, Chamado chamado) {
        if (!podeAcessar(usuario, chamado)) {
            throw new AccessDeniedException("Ticket is outside the user's current scope");
        }
    }

    private static boolean estaNaFila(Chamado chamado) {
        return chamado.getStatus() == StatusChamado.ABERTO && chamado.getAtendente() == null;
    }

    private static boolean mesmoUsuario(Usuario relacionado, Usuario usuario) {
        return relacionado != null && Objects.equals(relacionado.getId(), usuario.getId());
    }
}
