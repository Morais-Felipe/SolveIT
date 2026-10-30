package com.solveit.service.notificacao;

import com.solveit.entity.Chamado;
import com.solveit.entity.Comentario;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Monta e publica as notificações dos eventos do chamado. Deve ser chamado dentro da
 * transação da operação: o evento só é entregue se ela confirmar.
 *
 * <p>Regras de destinatários (plano 04-01): só usuários ativos, sem duplicatas e nunca o
 * autor da própria ação (D-A).
 */
@Component
public class NotificacaoPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final UsuarioRepository usuarioRepository;

    public NotificacaoPublisher(
            ApplicationEventPublisher eventPublisher,
            UsuarioRepository usuarioRepository) {
        this.eventPublisher = eventPublisher;
        this.usuarioRepository = usuarioRepository;
    }

    /** MAIL-01/02: todos os ADMINs ativos; CRITICA marca o assunto como urgente (D-C). */
    public void chamadoAberto(Chamado chamado, Usuario autor) {
        String assunto = assunto(chamado);
        if (chamado.getPrioridade() == Prioridade.CRITICA) {
            assunto = "[URGENTE] " + assunto;
        }
        String corpo = """
                Novo chamado aberto por %s.

                Prioridade: %s
                Categoria: %s

                %s
                """.formatted(
                chamado.getSolicitante().getNome(),
                chamado.getPrioridade(),
                chamado.getCategoria().getNome(),
                chamado.getDescricao());
        publicar("CHAMADO_ABERTO", chamado, autor,
                usuarioRepository.findByPerfilAndAtivoTrueOrderByIdAsc(PerfilUsuario.ADMIN),
                assunto, corpo);
    }

    /** MAIL-03: atendente atribuído e solicitante. */
    public void atendenteAtribuido(Chamado chamado, Usuario autor) {
        String corpo = """
                O chamado foi atribuído a %s por %s e está EM_ANDAMENTO.
                """.formatted(chamado.getAtendente().getNome(), autor.getNome());
        publicar("ATENDENTE_ATRIBUIDO", chamado, autor,
                List.of(chamado.getAtendente(), chamado.getSolicitante()),
                assunto(chamado), corpo);
    }

    /** MAIL-04: o solicitante, a cada mudança de status (inclui devolução à fila). */
    public void statusAlterado(
            Chamado chamado,
            StatusChamado anterior,
            Usuario autor,
            String observacao) {
        StringBuilder corpo = new StringBuilder("Status alterado de %s para %s por %s."
                .formatted(anterior, chamado.getStatus(), autor.getNome()));
        if (observacao != null && !observacao.isBlank()) {
            corpo.append("\n\nObservação: ").append(observacao);
        }
        corpo.append('\n');
        publicar("STATUS_ALTERADO", chamado, autor,
                List.of(chamado.getSolicitante()),
                assunto(chamado), corpo.toString());
    }

    /** MAIL-05: solicitante e atendente atual (D-B). */
    public void comentarioAdicionado(Comentario comentario) {
        Chamado chamado = comentario.getChamado();
        Usuario autor = comentario.getAutor();
        List<Usuario> envolvidos = new ArrayList<>();
        envolvidos.add(chamado.getSolicitante());
        if (chamado.getAtendente() != null) {
            envolvidos.add(chamado.getAtendente());
        }
        String corpo = """
                %s comentou:

                %s
                """.formatted(autor.getNome(), comentario.getTexto());
        publicar("COMENTARIO_ADICIONADO", chamado, autor, envolvidos, assunto(chamado), corpo);
    }

    private void publicar(
            String tipo,
            Chamado chamado,
            Usuario autor,
            Collection<Usuario> candidatos,
            String assunto,
            String corpo) {
        Set<String> destinatarios = new LinkedHashSet<>();
        for (Usuario candidato : candidatos) {
            if (candidato != null
                    && Boolean.TRUE.equals(candidato.getAtivo())
                    && !Objects.equals(candidato.getId(), autor.getId())) {
                destinatarios.add(candidato.getEmail());
            }
        }
        if (destinatarios.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new EmailNotificacao(
                tipo,
                chamado.getId(),
                List.copyOf(destinatarios),
                assunto,
                corpo));
    }

    private static String assunto(Chamado chamado) {
        return "[SolveIT] #%d %s".formatted(chamado.getId(), chamado.getTitulo());
    }
}
