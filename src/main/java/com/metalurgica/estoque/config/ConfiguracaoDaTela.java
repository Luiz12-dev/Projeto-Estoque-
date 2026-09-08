package com.metalurgica.estoque.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Faz a aplicação servir a própria tela, a partir de {@code resources/static}.
 * <p>
 * Na oficina não existe um segundo servidor para o frontend: é um processo só,
 * um endereço só. Isso elimina o Node da máquina do cliente e, de quebra,
 * elimina o CORS — a tela e a API passam a ter a mesma origem, e a classe de
 * problema em que o navegador leva 403 no login enquanto o curl funciona deixa
 * de existir.
 * <p>
 * O Angular usa rotas do lado do cliente: quem digita
 * {@code http://maquina:8080/cortes} e aperta Enter faz o navegador pedir esse
 * caminho ao servidor, que não tem arquivo nenhum ali. Sem o encaminhamento
 * abaixo o resultado seria 404 ao recarregar qualquer página que não a inicial
 * — defeito que só aparece depois de instalado, quando alguém aperta F5.
 * <p>
 * O padrão exclui {@code /api}, para uma rota de API inexistente continuar
 * respondendo 404 de verdade em vez de devolver HTML.
 */
@Configuration
public class ConfiguracaoDaTela implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        // Um nível: /cortes, /produtos, /login
        registro.addViewController("/{caminho:^(?!api|assets)[\\w-]+}")
                .setViewName("forward:/index.html");

        // Dois níveis: /ordens-servico/12
        registro.addViewController("/{caminho:^(?!api|assets)[\\w-]+}/{sub:[\\w-]+}")
                .setViewName("forward:/index.html");
    }
}
