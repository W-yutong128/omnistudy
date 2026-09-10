package com.omnistudy.ai;

public record AiRequest(
        String systemPrompt,
        String userPrompt,
        String imageBase64,
        boolean jsonOutput
) {
    public static AiRequest text(String systemPrompt, String userPrompt) {
        return new AiRequest(systemPrompt, userPrompt, null, true);
    }

    public static AiRequest vision(String systemPrompt, String userPrompt, String imageBase64) {
        return new AiRequest(systemPrompt, userPrompt, imageBase64, true);
    }

    public boolean hasImage() {
        return imageBase64 != null && !imageBase64.isBlank();
    }
}
