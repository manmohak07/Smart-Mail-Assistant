package com.email_writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import java.time.Duration;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.io.IOException;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        String prompt = buildPrompt(emailRequest);

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[] {
                        Map.of("parts", new Object[] {
                                Map.of("text", prompt)
                        })
                }
        );

        String response = webClient.post()
                .uri(geminiApiUrl + geminiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10))
                        .filter(throwable -> {
                            if (throwable instanceof WebClientResponseException) {
                                int code = ((WebClientResponseException) throwable).getStatusCode().value();
                                return code == 429 || (code >= 500 && code < 600);
                            }
                            return throwable instanceof IOException;
                        }))
                .block();
        
        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response);

            return root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();
        } catch (Exception exception) {
            return "Error processing the request " + exception.getMessage();
        }
    }


    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder builder = new StringBuilder();
        builder.append("Generate an email reply for the following email content." +
                        "Look at the tone required and the mail body attached below." +
                        "We don't want to include any subject, or any text modifier symbols such as * for bold." +
                        "Just return a plain text in a proper email format(greeting, body, thanking you and your name), which could be the best reply for the mail." +
                        "No suggestions, no nothing, no extras. Just build the reply."
                );

        if(emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) builder.append("Use a ").append(emailRequest.getTone()).append(" tone.");

        builder.append("\n Original Email: ").append(emailRequest.getEmailContent());

        return builder.toString();
    }
}
