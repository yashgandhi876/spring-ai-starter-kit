package dev.starterkit.ai.usage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UsageController {

    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    /** The caller's usage today and the limits that apply. */
    @GetMapping("/usage/me")
    public UsageService.Today mine(HttpServletRequest http) {
        return usage.today(UsageSubjects.of(http));
    }

    /**
     * Everyone's usage and cost, for admins. Only exists when login is on: without login it would show
     * every visitor's IP address to anyone.
     */
    @Validated
    @RestController
    @RequestMapping("/api/admin")
    @ConditionalOnProperty(name = "starter-kit.security.enabled", havingValue = "true")
    static class Admin {

        private final UsageService usage;

        Admin(UsageService usage) {
            this.usage = usage;
        }

        @GetMapping("/usage")
        public UsageService.Report report(@RequestParam(defaultValue = "30") @Min(1) @Max(366) int days) {
            return usage.report(days);
        }
    }
}
