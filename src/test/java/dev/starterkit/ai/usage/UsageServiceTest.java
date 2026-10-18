package dev.starterkit.ai.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import dev.starterkit.ai.testsupport.TestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

/** Runs against a real Postgres, so the upserts and the atomic limit check are really exercised. */
class UsageServiceTest {

    private static final Instant NOON = Instant.parse("2026-10-18T12:00:00Z");

    private final NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(TestDatabase.dataSource());
    private StarterKitProperties properties;

    @BeforeEach
    void clean() {
        TestDatabase.truncate("usage_daily");
        properties = new StarterKitProperties();
    }

    private UsageService service(Instant now) {
        PriceList prices = new PriceList(properties, new MockEnvironment()
                .withProperty("spring.ai.model.chat", "anthropic")
                .withProperty("spring.ai.anthropic.chat.options.model", "claude-sonnet-5-5"));
        return new UsageService(jdbc, properties, prices, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void countsRequestsTokensAndCostPerDay() {
        UsageService usage = service(NOON);

        usage.reserve("user:alice");
        UsageService.AnswerUsage first = usage.record("user:alice", new TokenUsage(1_000, 500));
        usage.reserve("user:alice");
        usage.record("user:alice", new TokenUsage(2_000, 0));

        // Claude Sonnet 5.5: $2 per million input, $10 per million output
        assertThat(first).isEqualTo(new UsageService.AnswerUsage(1_000, 500, 0.007));
        UsageService.Today today = usage.today("user:alice");
        assertThat(today.day()).isEqualTo("2026-10-18");
        assertThat(today.requests()).isEqualTo(2);
        assertThat(today.inputTokens()).isEqualTo(3_000);
        assertThat(today.outputTokens()).isEqualTo(500);
        assertThat(today.costUsd()).isEqualTo(0.011);
        assertThat(usage.today("user:bob").requests()).isZero();
    }

    @Test
    void stopsAtTheDailyRequestLimitAndSaysWhenItResets() {
        properties.getUsage().setDailyRequestLimit(2);
        UsageService usage = service(NOON);
        usage.reserve("ip:10.0.0.1");
        usage.reserve("ip:10.0.0.1");

        assertThatThrownBy(() -> usage.reserve("ip:10.0.0.1"))
                .isInstanceOf(UsageLimitExceededException.class)
                .hasMessage("You have used all 2 AI requests for today. The limit resets at midnight UTC.")
                .extracting(e -> ((UsageLimitExceededException) e).retryAfter())
                .isEqualTo(Duration.ofHours(12));
        // a refused request is not counted, and other subjects are not affected
        assertThat(usage.today("ip:10.0.0.1").requests()).isEqualTo(2);
        usage.reserve("ip:10.0.0.2");
    }

    @Test
    void stopsOnceTheDailyTokenLimitIsUsed() {
        properties.getUsage().setDailyTokenLimit(1_000);
        UsageService usage = service(NOON);
        usage.reserve("user:alice");
        usage.record("user:alice", new TokenUsage(900, 99));
        usage.reserve("user:alice"); // 999 used: still allowed
        usage.record("user:alice", new TokenUsage(1, 0));

        assertThatThrownBy(() -> usage.reserve("user:alice"))
                .isInstanceOf(UsageLimitExceededException.class)
                .hasMessageStartingWith("You have used all 1,000 AI tokens for today.");
    }

    @Test
    void limitsStartAgainOnTheNextUtcDay() {
        properties.getUsage().setDailyRequestLimit(1);
        service(NOON).reserve("user:alice");
        assertThatThrownBy(() -> service(NOON).reserve("user:alice")).isInstanceOf(UsageLimitExceededException.class);

        service(Instant.parse("2026-10-19T00:00:01Z")).reserve("user:alice");
    }

    @Test
    void parallelRequestsCannotSlipPastTheLimit() throws Exception {
        properties.getUsage().setDailyRequestLimit(5);
        UsageService usage = service(NOON);
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            calls.add(() -> {
                try {
                    usage.reserve("user:alice");
                    return true;
                } catch (UsageLimitExceededException e) {
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            long allowed = 0;
            for (Future<Boolean> f : pool.invokeAll(calls)) {
                allowed += f.get() ? 1 : 0;
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(usage.today("user:alice").requests()).isEqualTo(5);
    }

    @Test
    void tokensFinishingAfterMidnightGoToTheNewDay() {
        service(Instant.parse("2026-10-18T23:59:59Z")).reserve("user:alice");
        service(Instant.parse("2026-10-19T00:00:02Z")).record("user:alice", new TokenUsage(10, 5));

        UsageService.Today nextDay = service(Instant.parse("2026-10-19T01:00:00Z")).today("user:alice");
        assertThat(nextDay.requests()).isZero();
        assertThat(nextDay.inputTokens()).isEqualTo(10);
    }

    @Test
    void reportListsEverySubjectNewestFirstWithTotals() {
        UsageService yesterday = service(NOON.minus(Duration.ofDays(1)));
        yesterday.reserve("user:bob");
        yesterday.record("user:bob", new TokenUsage(1_000_000, 0));
        UsageService today = service(NOON);
        today.reserve("user:alice");
        today.record("user:alice", new TokenUsage(0, 100_000));
        service(NOON.minus(Duration.ofDays(40))).record("user:old", new TokenUsage(5, 5));

        UsageService.Report report = today.report(7);

        assertThat(report.from()).isEqualTo("2026-10-12");
        assertThat(report.to()).isEqualTo("2026-10-18");
        assertThat(report.model()).isEqualTo("claude-sonnet-5-5");
        assertThat(report.rows()).extracting(UsageService.DailyRow::subject, UsageService.DailyRow::day)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user:alice", "2026-10-18"),
                        org.assertj.core.groups.Tuple.tuple("user:bob", "2026-10-17"));
        assertThat(report.requests()).isEqualTo(2);
        assertThat(report.inputTokens()).isEqualTo(1_000_000);
        assertThat(report.outputTokens()).isEqualTo(100_000);
        assertThat(report.costUsd()).isEqualTo(3.0); // $2 input + $1 output
    }
}
