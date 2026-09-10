package com.omnistudy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnistudy.service.AiCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleAdapter implements AiModelAdapter {

    @Qualifier("dashscopeWebClient")
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Override
    public String provider() {
        return "dashscope";
    }

    @Override
    public AiResult generate(ModelRoute route, AiRequest request, AiCredentialService.ResolvedCredential credential) {
        if (request.hasImage() && !route.vision()) {
            throw new IllegalArgumentException("模型 " + route.model() + " 不支持图片，但当前任务需要视觉能力");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", route.model());
        body.put("max_tokens", route.maxTokens());
        body.put("temperature", route.temperature());
        if (request.jsonOutput()) {
            body.putObject("response_format").put("type", "json_object");
        }

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        if (request.hasImage()) addVisionMessage(messages, request);
        else messages.addObject().put("role", "user").put("content", request.userPrompt());

        String raw = webClient.post()
                .uri(credential.baseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.apiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseResult(raw);
    }

    private void addVisionMessage(ArrayNode messages, AiRequest request) {
        ObjectNode userMessage = messages.addObject().put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        String imageData = request.imageBase64().replaceFirst("^data:image/\\w+;base64,", "");
        content.addObject().put("type", "image_url").putObject("image_url")
                .put("url", "data:image/jpeg;base64," + imageData).put("detail", "low");
        content.addObject().put("type", "text").put("text", request.userPrompt());
    }

    private AiResult parseResult(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) throw new IllegalStateException("响应缺少 choices");
            JsonNode choice = choices.get(0);
            String finishReason = choice.path("finish_reason").asText("");
            String content = choice.path("message").path("content").asText();
            if (content.isBlank()) throw new IllegalStateException("模型返回了空内容");
            if ("length".equals(finishReason)) {
                throw new AiOutputTruncatedException("模型输出达到 token 上限，JSON 未完整返回");
            }
            JsonNode usage = root.path("usage");
            int inputTokens = usage.path("prompt_tokens").asInt(0);
            int outputTokens = usage.path("completion_tokens").asInt(0);
            int cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
            return new AiResult(content, inputTokens, outputTokens, cachedTokens);
        } catch (AiOutputTruncatedException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 响应解析失败，原始响应: {}", raw, e);
            throw new IllegalStateException("AI 响应解析失败", e);
        }
    }
}
