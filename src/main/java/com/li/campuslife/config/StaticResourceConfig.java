package com.li.campuslife.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 把本地上传目录映射成 /imgs/**，和库里存的前缀对齐（{@code /imgs/blogs/1/2/x.jpg}）。
 *
 * <p>上传接口返回的是 /blogs/d1/d2/xx.jpg，前端拼上 /imgs 前缀即可访问。</p>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    @Value("${campus.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = new File(uploadDir).getAbsoluteFile().toURI().toString();
        log.info("[static] 上传目录映射 /imgs/** -> {}", location);
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
