package com.solveit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Percorre todas as combinações de origem × destino × ator e compara a política com um
 * oráculo escrito à parte, direto do contrato do plano 03-07.
 */
class ChamadoTransitionPolicyTest {

    private enum Resultado { PERMITIDA, PROIBIDA_403, INVALIDA_409 }

    private enum Ator {
        ADMIN,
        SOLICITANTE_DONO,
        SOLICITANTE_ALHEIO,
        ATENDENTE_RESPONSAVEL,
        ATENDENTE_OUTRO,
        ATENDENTE_DONO
    }

    private record Aresta(StatusChamado origem, StatusChamado destino) {
    }

    /** As únicas transições manuais previstas no contrato. */
    private static final Set<Aresta> EXISTENTES = Set.of(
            new Aresta(StatusChamado.EM_ANDAMENTO, StatusChamado.RESOLVIDO),
            new Aresta(StatusChamado.RESOLVIDO, StatusChamado.FECHADO),
            new Aresta(StatusChamado.ABERTO, StatusChamado.CANCELADO),
            new Aresta(StatusChamado.EM_ANDAMENTO, StatusChamado.CANCELADO));

    private final ChamadoTransitionPolicy policy = new ChamadoTransitionPolicy();

    @Test
    void matchesTheContractForEveryOriginDestinationAndActor() {
        List<String> divergencias = new ArrayList<>();
        int combinacoes = 0;

        for (StatusChamado origem : StatusChamado.values()) {
            for (StatusChamado destino : StatusChamado.values()) {
                for (Ator ator : Ator.values()) {
                    combinacoes++;
                    Resultado esperado = oraculo(origem, destino, ator);
                    Resultado obtido = avaliar(origem, destino, ator);
                    if (esperado != obtido) {
                        divergencias.add("%s -> %s por %s: esperado %s, obtido %s"
                                .formatted(origem, destino, ator, esperado, obtido));
                    }
                }
            }
        }

        assertThat(combinacoes).isEqualTo(5 * 5 * 6);
        assertThat(divergencias).isEmpty();
    }

    @Test
    void finalStatesNeverLeaveAndNothingReopens() {
        for (StatusChamado destino : StatusChamado.values()) {
            for (Ator ator : Ator.values()) {
                assertThat(avaliar(StatusChamado.FECHADO, destino, ator))
                        .isEqualTo(Resultado.INVALIDA_409);
                assertThat(avaliar(StatusChamado.CANCELADO, destino, ator))
                        .isEqualTo(Resultado.INVALIDA_409);
            }
        }
        for (Ator ator : Ator.values()) {
            assertThat(avaliar(StatusChamado.RESOLVIDO, StatusChamado.EM_ANDAMENTO, ator))
                    .isEqualTo(Resultado.INVALIDA_409);
            assertThat(avaliar(StatusChamado.EM_ANDAMENTO, StatusChamado.ABERTO, ator))
                    .isEqualTo(Resultado.INVALIDA_409);
            assertThat(avaliar(StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO, ator))
                    .as("atribuição tem rota própria")
                    .isEqualTo(Resultado.INVALIDA_409);
        }
    }

    @Test
    void adminDoesNotInheritRequesterDecisions() {
        assertThat(avaliar(StatusChamado.RESOLVIDO, StatusChamado.FECHADO, Ator.ADMIN))
                .isEqualTo(Resultado.PROIBIDA_403);
        assertThat(avaliar(StatusChamado.ABERTO, StatusChamado.CANCELADO, Ator.ADMIN))
                .isEqualTo(Resultado.PROIBIDA_403);
        assertThat(avaliar(StatusChamado.RESOLVIDO, StatusChamado.FECHADO, Ator.ATENDENTE_DONO))
                .isEqualTo(Resultado.PROIBIDA_403);
    }

    private static Resultado oraculo(StatusChamado origem, StatusChamado destino, Ator ator) {
        Aresta aresta = new Aresta(origem, destino);
        if (!EXISTENTES.contains(aresta)) {
            return Resultado.INVALIDA_409;
        }
        boolean permitido = switch (destino) {
            case RESOLVIDO -> ator == Ator.ADMIN || ator == Ator.ATENDENTE_RESPONSAVEL;
            case FECHADO -> ator == Ator.SOLICITANTE_DONO;
            case CANCELADO -> origem == StatusChamado.ABERTO
                    ? ator == Ator.SOLICITANTE_DONO
                    : ator == Ator.ADMIN;
            default -> false;
        };
        return permitido ? Resultado.PERMITIDA : Resultado.PROIBIDA_403;
    }

    private Resultado avaliar(StatusChamado origem, StatusChamado destino, Ator ator) {
        Usuario solicitanteDono = usuario(1L, PerfilUsuario.SOLICITANTE);
        Usuario responsavel = usuario(2L, PerfilUsuario.ATENDENTE);
        Usuario atendenteDono = usuario(6L, PerfilUsuario.ATENDENTE);

        Usuario dono = ator == Ator.ATENDENTE_DONO ? atendenteDono : solicitanteDono;
        Usuario atendente = origem == StatusChamado.ABERTO || origem == StatusChamado.CANCELADO
                ? null
                : responsavel;
        Chamado chamado = new Chamado(
                "Titulo", "Descricao", origem, Prioridade.MEDIA,
                dono, atendente, new Categoria("Rede", null));

        Usuario quem = switch (ator) {
            case ADMIN -> usuario(3L, PerfilUsuario.ADMIN);
            case SOLICITANTE_DONO -> solicitanteDono;
            case SOLICITANTE_ALHEIO -> usuario(4L, PerfilUsuario.SOLICITANTE);
            case ATENDENTE_RESPONSAVEL -> responsavel;
            case ATENDENTE_OUTRO -> usuario(5L, PerfilUsuario.ATENDENTE);
            case ATENDENTE_DONO -> atendenteDono;
        };

        try {
            policy.exigirPermitida(quem, chamado, destino);
            return Resultado.PERMITIDA;
        } catch (AccessDeniedException exception) {
            return Resultado.PROIBIDA_403;
        } catch (DomainException exception) {
            assertThat(exception.getCode()).isEqualTo(ApiErrorCode.INVALID_STATE);
            return Resultado.INVALIDA_409;
        }
    }

    private static Usuario usuario(Long id, PerfilUsuario perfil) {
        Usuario usuario = new Usuario("Usuario " + id, id + "@example.com", "hash", perfil);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }
}
