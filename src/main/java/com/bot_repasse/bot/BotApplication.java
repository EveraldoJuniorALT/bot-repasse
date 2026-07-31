package com.bot_repasse.bot;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class BotApplication {

	public static void main(String[] args) {
		carregarVariaveisDeAmbiente();
		SpringApplication.run(BotApplication.class, args);
	}

	private static void carregarVariaveisDeAmbiente() {
		try {
			Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
			dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		} catch (Exception e) {
			// Ignora silenciosamente, delegando para as variáveis nativas do SO.
		}
	}

}
