// backend/src/main/java/com/example/pdfgen/config/WebConfig.java
package com.example.pdfgen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA(Single Page Application) 라우팅 지원 설정.
 * /api/** 경로 이외의 모든 요청을 index.html로 포워딩하여
 * React Router 등 클라이언트 사이드 라우팅이 정상 동작하도록 한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        // 실제 파일이 존재하면 그대로 반환, 아니면 index.html로 폴백
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
