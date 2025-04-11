package com.thederailingmafia.carwash.washerservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Try to get token from RequestContextHolder (incoming request)
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
                HttpServletRequest request = servletRequestAttributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                System.out.println("Feign raw header from request: " + authHeader);

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    System.out.println("Feign forwarding JWT from request: " + authHeader);
                    requestTemplate.header("Authorization", authHeader);
                    return; // Exit once token is set
                } else {
                    System.out.println("No valid Bearer token in Feign request header");
                }
            } else {
                System.out.println("No request context for Feign - Thread: " + Thread.currentThread().getName());
            }

            // Fallback to SecurityContextHolder
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() != null) {
                String token = auth.getCredentials().toString();
                if (token.startsWith("Bearer ")) {
                    System.out.println("Feign forwarding JWT from SecurityContext: " + token);
                    requestTemplate.header("Authorization", token);
                } else {
                    System.out.println("Feign SecurityContext token not Bearer: " + token);
                    requestTemplate.header("Authorization", "Bearer " + token);
                }
            } else {
                System.out.println("No JWT in SecurityContext for Feign");
            }
        };
    }
}