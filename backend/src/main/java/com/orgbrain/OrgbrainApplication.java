package com.orgbrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Locale;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrgbrainApplication {

	public static void main(String[] args) {
		// Mensajes de validación en español aunque el sistema operativo esté en otro idioma.
		Locale.setDefault(Locale.of("es", "ES"));
		SpringApplication.run(OrgbrainApplication.class, args);
	}

}
