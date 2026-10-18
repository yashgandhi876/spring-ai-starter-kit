package dev.starterkit.ai.usage;

import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Prices used for cost tracking, in US dollars per million tokens. AI_PRICE_INPUT_PER_MTOK and
 * AI_PRICE_OUTPUT_PER_MTOK win; otherwise the built-in price of the configured model is used.
 * Provider prices change: check your provider's pricing page and set the variables if they differ.
 */
@Component
public class PriceList {

    private static final Logger log = LoggerFactory.getLogger(PriceList.class);

    /** Input and output price per million tokens. */
    public record Price(double inputPerMtok, double outputPerMtok) {

        /** Cost in millionths of a dollar: price per million tokens is exactly micro-dollars per token. */
        public long costMicros(TokenUsage usage) {
            return Math.round(usage.inputTokens() * inputPerMtok + usage.outputTokens() * outputPerMtok);
        }
    }

    static final Price FREE = new Price(0, 0);

    /** Known list prices at the time of writing (2026-10). */
    static final Map<String, Price> KNOWN = Map.of(
            "claude-opus-5-5", new Price(4.00, 20.00),
            "claude-sonnet-5-5", new Price(2.00, 10.00),
            "claude-haiku-5-5", new Price(0.10, 0.50),
            "gpt-4o-mini", new Price(0.15, 0.60),
            "gpt-4o", new Price(2.50, 10.00));

    private final Price price;
    private final String model;

    public PriceList(StarterKitProperties properties, Environment env) {
        String provider = env.getProperty("spring.ai.model.chat", "ollama");
        this.model = switch (provider) {
            case "anthropic" -> env.getProperty("spring.ai.anthropic.chat.options.model", "");
            case "openai" -> env.getProperty("spring.ai.openai.chat.options.model", "");
            default -> env.getProperty("spring.ai.ollama.chat.options.model", "");
        };
        Price builtIn = "ollama".equals(provider) ? FREE : KNOWN.get(model);
        StarterKitProperties.Usage usage = properties.getUsage();
        Double in = usage.getPriceInputPerMtok();
        Double out = usage.getPriceOutputPerMtok();
        if (builtIn == null && (in == null || out == null)) {
            log.warn("No price known for model '{}'; costs are tracked as 0. Set AI_PRICE_INPUT_PER_MTOK and "
                    + "AI_PRICE_OUTPUT_PER_MTOK to track them.", model);
            builtIn = FREE;
        }
        this.price = new Price(in != null ? in : builtIn.inputPerMtok(), out != null ? out : builtIn.outputPerMtok());
    }

    public Price current() {
        return price;
    }

    public String model() {
        return model;
    }
}
