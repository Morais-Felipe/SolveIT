package com.solveit.service.notificacao;

import java.util.List;

/**
 * Evento publicado dentro da transação com tudo o que o e-mail precisa. Como já carrega
 * destinatários, assunto e corpo, o envio depois do commit não relê o banco.
 *
 * @param tipo nome do evento de domínio, usado só em logs
 * @param chamadoId chamado de origem, usado só em logs
 * @param destinatarios e-mails já sem duplicatas e sem o autor da ação
 */
public record EmailNotificacao(
        String tipo,
        Long chamadoId,
        List<String> destinatarios,
        String assunto,
        String corpo) {

    public EmailNotificacao {
        destinatarios = List.copyOf(destinatarios);
    }
}
