package com.omnistudy.agentscope;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Adds the current browser frame to model calls without storing its Base64 data in AgentState. */
@Component
public class ScreenshotContextMiddleware implements MiddlewareBase {
    private static final int MAX_BASE64_CHARS = 2_500_000;

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext runtimeContext, ReasoningInput input,
                                        Function<ReasoningInput, Flux<AgentEvent>> next) {
        AgentScopeRequestContext requestContext = runtimeContext.get(AgentScopeRequestContext.class);
        ImageData image = requestContext == null ? null : parse(requestContext.request().screenshot());
        if (image == null) return next.apply(input);

        ImageBlock block = ImageBlock.builder()
                .source(Base64Source.builder().mediaType(image.mediaType()).data(image.data()).build())
                .maxPixels(1_200_000)
                .build();
        Msg transientScreenshot = UserMessage.builder()
                .name("current_browser_frame")
                .content(block)
                .metadata(Map.of(Msg.METADATA_SYNTHETIC, true, "omnistudy_transient", true))
                .build();
        List<Msg> messages = new ArrayList<>(input.messages());
        messages.add(transientScreenshot);
        requestContext.screenshotInjected();
        return next.apply(new ReasoningInput(messages, input.tools(), input.options()));
    }

    private ImageData parse(String screenshot) {
        if (screenshot == null || screenshot.isBlank() || screenshot.length() > MAX_BASE64_CHARS) return null;
        String mediaType = "image/jpeg";
        String data = screenshot;
        if (screenshot.startsWith("data:")) {
            int separator = screenshot.indexOf(',');
            int typeEnd = screenshot.indexOf(';');
            if (separator < 0 || typeEnd < 5 || typeEnd > separator) return null;
            mediaType = screenshot.substring(5, typeEnd).toLowerCase();
            data = screenshot.substring(separator + 1);
        }
        if (!mediaType.startsWith("image/") || data.isBlank()) return null;
        return new ImageData(mediaType, data);
    }

    private record ImageData(String mediaType, String data) {}
}
