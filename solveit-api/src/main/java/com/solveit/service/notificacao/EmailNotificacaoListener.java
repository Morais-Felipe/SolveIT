package com.solveit.service.notificacao;

import com.solveit.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega as notificações somente depois do commit (MAIL-06) e fora da thread da
 * requisição. Se a transação for desfeita, o Spring descarta o evento e nada é enviado.
 * Falhas de SMTP são registradas em log e nunca chegam à operação já confirmada.
 */
@Component
public class EmailNotificacaoListener {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificacaoListener.class);

    private final JavaMailSender mailSender;
    private final boolean habilitado;
    private final String remetente;

    public EmailNotificacaoListener(
            JavaMailSender mailSender,
            @Value("${solveit.mail.enabled:true}") boolean habilitado,
            @Value("${solveit.mail.from}") String remetente) {
        this.mailSender = mailSender;
        this.habilitado = habilitado;
        this.remetente = remetente;
    }

    @Async(AsyncConfig.NOTIFICACAO_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(EmailNotificacao notificacao) {
        if (!habilitado) {
            return;
        }
        // Um e-mail por destinatário: ninguém vê o endereço dos outros.
        for (String destinatario : notificacao.destinatarios()) {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(destinatario);
            mensagem.setSubject(notificacao.assunto());
            mensagem.setText(notificacao.corpo());
            try {
                mailSender.send(mensagem);
            } catch (MailException exception) {
                LOG.warn("Falha ao enviar notificacao {} do chamado {}: {}",
                        notificacao.tipo(),
                        notificacao.chamadoId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
