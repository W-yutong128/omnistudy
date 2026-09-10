package com.omnistudy.ai;

public record ModelRoute(
        String provider,
        String model,
        int maxTokens,
        double temperature,
        boolean vision
) {}
