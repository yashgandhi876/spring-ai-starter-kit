package dev.starterkit.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Checks that customer settings bind correctly and that bad values stop startup with a clear message. */
class StarterKitPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(StarterKitProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAreValid() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            StarterKitProperties p = ctx.getBean(StarterKitProperties.class);
            assertThat(p.getRag().getTopK()).isEqualTo(4);
            assertThat(p.getPrompts().getRag()).contains("{context}", "{question}");
        });
    }

    @Test
    void bindsCustomerSettings() {
        runner.withPropertyValues(
                        "starter-kit.branding.app-name=Acme Assistant",
                        "starter-kit.branding.accent-color=#ff6600",
                        "starter-kit.prompts.system=You are Acme's support bot.",
                        "starter-kit.rag.top-k=8",
                        "starter-kit.rag.similarity-threshold=0.5",
                        "starter-kit.rag.chunk-size=500")
                .run(ctx -> {
                    StarterKitProperties p = ctx.getBean(StarterKitProperties.class);
                    assertThat(p.getBranding().getAppName()).isEqualTo("Acme Assistant");
                    assertThat(p.getBranding().getAccentColor()).isEqualTo("#ff6600");
                    assertThat(p.getPrompts().getSystem()).isEqualTo("You are Acme's support bot.");
                    assertThat(p.getRag().getTopK()).isEqualTo(8);
                    assertThat(p.getRag().getSimilarityThreshold()).isEqualTo(0.5);
                    assertThat(p.getRag().getChunkSize()).isEqualTo(500);
                });
    }

    @Test
    void rejectsAccentColourThatIsNotHex() {
        runner.withPropertyValues("starter-kit.branding.accent-color=red; background:url(x)")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("accentColor"));
    }

    @Test
    void rejectsRagPromptMissingPlaceholders() {
        runner.withPropertyValues("starter-kit.prompts.rag=Answer: {question}")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("{context}"));
    }

    @Test
    void rejectsTopKOutOfRange() {
        runner.withPropertyValues("starter-kit.rag.top-k=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsSimilarityThresholdAboveOne() {
        runner.withPropertyValues("starter-kit.rag.similarity-threshold=1.5")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
