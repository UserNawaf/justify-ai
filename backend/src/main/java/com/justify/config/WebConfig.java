package com.justify.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(JustifyProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final JustifyProperties props;

    public WebConfig(JustifyProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = props.security().corsOrigins().stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (!origins.isEmpty()) {
            registry.addMapping("/api/**")
                    .allowedOrigins(origins.toArray(String[]::new))
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("Content-Type", ApiKeyFilter.HEADER)
                    .maxAge(3600);
        }
    }

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter() {
        FilterRegistrationBean<ApiKeyFilter> reg = new FilterRegistrationBean<>(new ApiKeyFilter(props.security().apiKey()));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}
