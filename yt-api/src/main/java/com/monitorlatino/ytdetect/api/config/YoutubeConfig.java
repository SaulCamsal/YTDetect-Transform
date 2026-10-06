package com.monitorlatino.ytdetect.api.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YoutubeConfig {

    private static final Logger log = LoggerFactory.getLogger(YoutubeConfig.class);

    private final String applicationName;
    private final String apiKey;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    public YoutubeConfig(
            @Value("${yt.youtube.application-name:YTDetect-Transform}") String applicationName,
            @Value("${yt.youtube.api-key:}") String apiKey,
            @Value("${yt.youtube.client-id:}") String clientId,
            @Value("${yt.youtube.client-secret:}") String clientSecret,
            @Value("${yt.youtube.refresh-token:}") String refreshToken) {
        this.applicationName = applicationName;
        this.apiKey = apiKey;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    @Bean
    public YouTube youTube() {
        NetHttpTransport httpTransport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        if (clientId != null && !clientId.isBlank() &&
                clientSecret != null && !clientSecret.isBlank() &&
                refreshToken != null && !refreshToken.isBlank()) {

            log.info("Configuring YouTube client with OAuth2 refresh token");
            UserCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();

            return new YouTube.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                    .setApplicationName(applicationName)
                    .build();
        } else {
            log.info("Configuring YouTube client without OAuth2 (fallback)");
            return new YouTube.Builder(httpTransport, jsonFactory, httpRequest -> {
            })
                    .setApplicationName(applicationName)
                    .build();
        }
    }

    public String getApiKey() {
        return apiKey;
    }
}
