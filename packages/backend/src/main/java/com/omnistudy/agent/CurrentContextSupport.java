package com.omnistudy.agent;
import com.omnistudy.model.dto.AgentChatResponse;
import java.util.List;
final class CurrentContextSupport {
    private CurrentContextSupport() {}
    static AgentToolResult execute(AgentExecutionContext context) {
        var request = context.request();
        String time = formatTime(request.currentTime());
        String observation = """
                课程：%s
                当前分集：%d
                当前时间：%s
                前文字幕：%s
                当前字幕：%s
                后文字幕：%s
                """.formatted(value(context.session().getVideoTitle()), request.part() == null ? 1 : request.part(),
                time, value(request.before()), value(request.current()), value(request.after()));
        return new AgentToolResult(observation, List.of(new AgentChatResponse.Source(
                "当前视频 · " + time, truncate(value(request.current()), 240),
                request.part() == null ? 1 : request.part(), request.currentTime())), null, null);
    }
    private static String value(String value) { return value == null ? "" : value; }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private static String formatTime(Double seconds) { int total = Math.max(0, (int) Math.round(seconds == null ? 0 : seconds)); return "%d:%02d".formatted(total / 60, total % 60); }
}
