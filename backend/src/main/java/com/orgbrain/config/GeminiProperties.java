package com.orgbrain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgbrain.gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl, int timeoutSeconds) {

	public boolean configurada() {
		return apiKey != null && !apiKey.isBlank();
	}
}
