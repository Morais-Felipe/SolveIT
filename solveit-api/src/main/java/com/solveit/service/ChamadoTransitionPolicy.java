package com.solveit.service;

import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.StatusChamado;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Tabela das transições manuais de status. Cada linha liga (origem, destino) à regra de
 * quem pode executá-la. Se a linha não existe, a transição é inválida (409); se existe
 * mas a regra recusa o ator, falta permissão (403). ABERTO → EM_ANDAMENTO não aparece
 * aqui: só acontece pela rota de atribuição. Nenhuma linha sai de FECHADO ou CANCELADO.
 */
@Component
public class ChamadoTransitionPolicy {

    private record Transicao(StatusChamado origem, StatusChamado destino) {
    }

    private static final Map<Transicao, BiPredicate<Usuario, Chamado>> PERMITIDAS = Map.of(
            new Transicao(StatusChamado.EM_ANDAMENTO, StatusChamado.RESOLVIDO),
            (usuario, chamado) -> eAdmin(usuario) || eAtendenteResponsavel(usuario, chamado),
            new Transicao(StatusChamado.RESOLVIDO, StatusChamado.FECHADO),
            ChamadoTransitionPolicy::eSolicitanteDono,
            new Transicao(StatusChamado.ABERTO, StatusChamado.CANCELADO),
            ChamadoTransitionPolicy::eSolicitanteDono,
            new Transicao(StatusChamado.EM_ANDAMENTO, StatusChamado.CANCELADO),
            (usuario, chamado) -> eAdmin(usuario));

    public void exigirPermitida(Usuario usuario, Chamado chamado, StatusChamado destino) {
        BiPredicate<Usuario, Chamado> regra =
                PERMITIDAS.get(new Transicao(chamado.getStatus(), destino));
        if (regra == null) {
            throw new DomainException(
                    ApiErrorCode.INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Transição inválida",
                    "O chamado não pode passar do status atual para o status solicitado.");
        }
        if (!regra.test(usuario, chamado)) {
            throw new AccessDeniedException("Actor is not allowed to perform this transition");
        }
    }

    private static boolean eAdmin(Usuario usuario) {
        return usuario.getPerfil() == PerfilUsuario.ADMIN;
    }

    /** Exige o perfil SOLICITANTE: um atendente que abriu o chamado não herda esse papel. */
    private static boolean eSolicitanteDono(Usuario usuario, Chamado chamado) {
        return usuario.getPerfil() == PerfilUsuario.SOLICITANTE
                && Objects.equals(chamado.getSolicitante().getId(), usuario.getId());
    }

    private static boolean eAtendenteResponsavel(Usuario usuario, Chamado chamado) {
        return usuario.getPerfil() == PerfilUsuario.ATENDENTE
                && chamado.getAtendente() != null
                && Objects.equals(chamado.getAtendente().getId(), usuario.getId());
    }
}
