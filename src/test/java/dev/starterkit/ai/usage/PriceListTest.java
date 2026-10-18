package dev.starterkit.ai.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PriceListTest {

    private final StarterKitProperties properties = new StarterKitProperties();

    private PriceList prices(String provider, String modelProperty, String model) {
        MockEnvironment env = new MockEnvironment().withProperty("spring.ai.model.chat", provider);
        if (modelProperty != null) {
            env.withProperty(modelProperty, model);
        }
        return new PriceList(properties, env);
    }

    @Test
    void localOllamaModelsAreFree() {
        PriceList p = prices("ollama", "spring.ai.ollama.chat.options.model", "llama3.2");

        assertThat(p.current()).isEqualTo(PriceList.FREE);
        assertThat(p.model()).isEqualTo("llama3.2");
    }

    @Test
    void usesTheBuiltInPriceOfKnownModels() {
        assertThat(prices("anthropic", "spring.ai.anthropic.chat.options.model", "claude-sonnet-5-5").current())
                .isEqualTo(new PriceList.Price(2.00, 10.00));
        assertThat(prices("openai", "spring.ai.openai.chat.options.model", "gpt-4o-mini").current())
                .isEqualTo(new PriceList.Price(0.15, 0.60));
    }

    @Test
    void configuredPricesWin() {
        properties.getUsage().setPriceInputPerMtok(1.5);
        properties.getUsage().setPriceOutputPerMtok(7.0);

        assertThat(prices("anthropic", "spring.ai.anthropic.chat.options.model", "claude-sonnet-5-5").current())
                .isEqualTo(new PriceList.Price(1.5, 7.0));
    }

    @Test
    void unknownModelsCostNothingUntilPricesAreSet() {
        assertThat(prices("openai", "spring.ai.openai.chat.options.model", "some-new-model").current())
                .isEqualTo(PriceList.FREE);
    }

    @Test
    void costIsInMillionthsOfADollar() {
        PriceList.Price sonnet = new PriceList.Price(2.00, 10.00);

        assertThat(sonnet.costMicros(new TokenUsage(1_000_000, 0))).isEqualTo(2_000_000);
        assertThat(sonnet.costMicros(new TokenUsage(150, 40))).isEqualTo(700); // $0.0007
        assertThat(sonnet.costMicros(TokenUsage.NONE)).isZero();
    }
}
