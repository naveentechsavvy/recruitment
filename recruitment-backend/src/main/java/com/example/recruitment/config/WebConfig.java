package com.example.recruitment.config;

import com.example.recruitment.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final FileStorageService fileStorage;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins, FileStorageService fileStorage) {
        this.allowedOrigins = allowedOrigins;
        this.fileStorage = fileStorage;
    }

    /** Lets the Angular dev server (http://localhost:4200) call this API from the browser. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/uploads/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
    }

    /** Serves uploaded logos at http://localhost:8080/uploads/logos/<file>. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(fileStorage.getUploadRootUri());
    }
}
