package dev.starterkit.ai.usage;

import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Counts AI requests, tokens and cost per subject per UTC day, and enforces the daily limits.
 * Call {@link #reserve} before an AI call and {@link #record} after it.
 */
@Service
public class UsageService {

    /** What one answer cost, returned to the client with the answer. */
    public record AnswerUsage(long inputTokens, long outputTokens, double costUsd) {}

    /** Today's totals for one subject, with the limits that apply. 0 means no limit. */
    public record Today(String day, int requests, long inputTokens, long outputTokens, double costUsd,
                        int requestLimit, long tokenLimit) {}

    /** One subject's usage on one day, for the admin report. */
    public record DailyRow(String subject, String day, int requests, long inputTokens, long outputTokens,
                           double costUsd) {}

    /** The admin report: rows, newest day first, and their totals. */
    public record Report(String from, String to, String model, List<DailyRow> rows, int requests,
                         long inputTokens, long outputTokens, double costUsd) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final StarterKitProperties.Usage limits;
    private final PriceList prices;
    private final Clock clock;

    @Autowired
    public UsageService(NamedParameterJdbcTemplate jdbc, StarterKitProperties properties, PriceList prices) {
        this(jdbc, properties, prices, Clock.systemUTC());
    }

    UsageService(NamedParameterJdbcTemplate jdbc, StarterKitProperties properties, PriceList prices, Clock clock) {
        this.jdbc = jdbc;
        this.limits = properties.getUsage();
        this.prices = prices;
        this.clock = clock;
    }

    /**
     * Counts one request for today, or throws if a daily limit is already used up. The check and the
     * count are one statement, so parallel requests cannot slip past the limit. The token limit is
     * checked against tokens already used: the request that crosses it is still answered.
     */
    public void reserve(String subject) {
        LocalDate day = today();
        List<Integer> counted = jdbc.queryForList("""
                INSERT INTO usage_daily (subject, day, requests) VALUES (:subject, :day, 1)
                ON CONFLICT (subject, day) DO UPDATE SET requests = usage_daily.requests + 1
                WHERE (:requestLimit = 0 OR usage_daily.requests < :requestLimit)
                  AND (:tokenLimit = 0 OR usage_daily.input_tokens + usage_daily.output_tokens < :tokenLimit)
                RETURNING requests""",
                params(subject, day)
                        .addValue("requestLimit", limits.getDailyRequestLimit())
                        .addValue("tokenLimit", limits.getDailyTokenLimit()),
                Integer.class);
        if (counted.isEmpty()) {
            Today now = today(subject);
            String message = limits.getDailyRequestLimit() > 0 && now.requests() >= limits.getDailyRequestLimit()
                    ? "You have used all %d AI requests for today.".formatted(limits.getDailyRequestLimit())
                    : "You have used all %,d AI tokens for today.".formatted(limits.getDailyTokenLimit());
            throw new UsageLimitExceededException(message + " The limit resets at midnight UTC.", untilMidnight());
        }
    }

    /** Adds the tokens and cost of a finished AI call to today's totals. */
    public AnswerUsage record(String subject, TokenUsage usage) {
        long costMicros = prices.current().costMicros(usage);
        // An upsert, not an update: the call may have finished after midnight, on a day with no row yet
        jdbc.update("""
                INSERT INTO usage_daily (subject, day, input_tokens, output_tokens, cost_micros)
                VALUES (:subject, :day, :input, :output, :cost)
                ON CONFLICT (subject, day) DO UPDATE SET
                    input_tokens = usage_daily.input_tokens + EXCLUDED.input_tokens,
                    output_tokens = usage_daily.output_tokens + EXCLUDED.output_tokens,
                    cost_micros = usage_daily.cost_micros + EXCLUDED.cost_micros""",
                params(subject, today())
                        .addValue("input", usage.inputTokens())
                        .addValue("output", usage.outputTokens())
                        .addValue("cost", costMicros));
        return new AnswerUsage(usage.inputTokens(), usage.outputTokens(), dollars(costMicros));
    }

    public Today today(String subject) {
        LocalDate day = today();
        List<Today> rows = jdbc.query("""
                SELECT requests, input_tokens, output_tokens, cost_micros FROM usage_daily
                WHERE subject = :subject AND day = :day""",
                params(subject, day),
                (rs, i) -> new Today(day.toString(), rs.getInt(1), rs.getLong(2), rs.getLong(3), dollars(rs.getLong(4)),
                        limits.getDailyRequestLimit(), limits.getDailyTokenLimit()));
        return rows.isEmpty()
                ? new Today(day.toString(), 0, 0, 0, 0, limits.getDailyRequestLimit(), limits.getDailyTokenLimit())
                : rows.getFirst();
    }

    /** Usage of every subject over the last {@code days} days, today included. */
    public Report report(int days) {
        LocalDate to = today();
        LocalDate from = to.minusDays(days - 1L);
        List<DailyRow> rows = jdbc.query("""
                SELECT subject, day, requests, input_tokens, output_tokens, cost_micros FROM usage_daily
                WHERE day BETWEEN :from AND :to
                ORDER BY day DESC, cost_micros DESC, subject""",
                Map.of("from", from, "to", to),
                (rs, i) -> new DailyRow(rs.getString(1), rs.getObject(2, LocalDate.class).toString(), rs.getInt(3),
                        rs.getLong(4), rs.getLong(5), dollars(rs.getLong(6))));
        return new Report(from.toString(), to.toString(), prices.model(), rows,
                rows.stream().mapToInt(DailyRow::requests).sum(),
                rows.stream().mapToLong(DailyRow::inputTokens).sum(),
                rows.stream().mapToLong(DailyRow::outputTokens).sum(),
                dollars(Math.round(rows.stream().mapToDouble(DailyRow::costUsd).sum() * 1_000_000)));
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    Duration untilMidnight() {
        var now = clock.instant();
        var midnight = today().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Duration.between(now, midnight);
    }

    private static MapSqlParameterSource params(String subject, LocalDate day) {
        return new MapSqlParameterSource().addValue("subject", subject).addValue("day", day);
    }

    private static double dollars(long micros) {
        return micros / 1_000_000.0;
    }
}
