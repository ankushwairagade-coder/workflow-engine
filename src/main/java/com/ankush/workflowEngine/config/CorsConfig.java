package com.ankush.workflowEngine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://54.224.229.238",
                        "http://54.224.229.238:3000",
                        "http://localhost:3000",
                        "http://localhost"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow specific origins
        config.addAllowedOrigin("http://54.224.229.238");
        config.addAllowedOrigin("http://54.224.229.238:3000");
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://localhost");

        // Allow all methods
        config.addAllowedMethod("*");

        // Allow all headers
        config.addAllowedHeader("*");

        // Allow credentials
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        // Apply to all API endpoints
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}



//package com.ankush.workflowEngine.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.springframework.web.filter.CorsFilter;
//
//import java.util.Arrays;
//
//@Configuration
//public class CorsConfig {
//
//    @Bean
//    public CorsFilter corsFilter() {
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        CorsConfiguration config = new CorsConfiguration();
//
//        // Allow credentials (cookies, authorization headers, etc.)
//        config.setAllowCredentials(true);
//
//        // Specify allowed origins (required when allowCredentials is true)
//        // For development, allow common frontend ports
//        config.setAllowedOrigins(Arrays.asList(
//            "http://localhost:3000",
//            "http://localhost:5173",
//            "http://localhost:5174",
//            "http://127.0.0.1:3000",
//            "http://127.0.0.1:5173"
//        ));
//
//        // For production, add your domain here:
//        // config.setAllowedOrigins(Arrays.asList("https://your-production-domain.com"));
//
//        // Allow all headers
//        config.setAllowedHeaders(Arrays.asList("*"));
//
//        // Allow all HTTP methods
//        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
//
//        // Expose headers that the frontend might need
//        config.setExposedHeaders(Arrays.asList(
//            "Authorization",
//            "Content-Type",
//            "X-Total-Count",
//            "Access-Control-Allow-Origin",
//            "Access-Control-Allow-Credentials"
//        ));
//
//        // Cache preflight requests for 1 hour
//        config.setMaxAge(3600L);
//
//        source.registerCorsConfiguration("/**", config);
//        return new CorsFilter(source);
//    }
//}
//
