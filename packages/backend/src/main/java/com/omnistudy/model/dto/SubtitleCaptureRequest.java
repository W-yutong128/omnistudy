package com.omnistudy.model.dto;

import java.util.List;

public record SubtitleCaptureRequest(Integer part, List<Chunk> chunks) {
    public record Chunk(Float tStart, Float tEnd, String text) {}
}
