package com.example.service_dependency_tracker.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves index.html for any path that doesn't match an API controller or a static asset.
 * Paths containing a dot (e.g. main.js, styles.css) are intentionally excluded so that
 * the default static-resource handler serves those files instead.
 */
@Controller
public class SpaFallbackController {

    @GetMapping(value = { "/", "/{path:[^\\.]*}", "/{path:[^\\.]*}/**" })
    public String index() {
        return "forward:/index.html";
    }
}
