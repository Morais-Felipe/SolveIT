package com.solveit.config;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita métodos {@code @Async} com um executor limitado para notificações. A fila
 * finita impede que um SMTP lento acumule threads sem limite. Se ela encher, a
 * notificação é descartada com um aviso em log: lançar exceção aqui chegaria à thread da
 * requisição depois do commit e transformaria uma operação confirmada em erro 500.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICACAO_EXECUTOR = "notificacaoExecutor";

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = NOTIFICACAO_EXECUTOR)
    public Executor notificacaoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notificacao-");
        executor.setRejectedExecutionHandler((tarefa, pool) ->
                LOG.warn("Fila de notificacoes cheia; notificacao descartada"));
        executor.initialize();
        return executor;
    }
}
