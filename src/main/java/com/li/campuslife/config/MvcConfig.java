package com.li.campuslife.config;

import com.li.campuslife.utils.LoginInterceptor;
import com.li.campuslife.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 处理刷新会话的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**");

        // 处理登录的拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/blog/hot",
                        "/blog/*",//笔记详情游客可读；发布/点赞仍需登录
                        "/shop/**",
                        "/item/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/campus/**",
                        "/upload/**",
                        "/admin/**");//放行的路径（/admin 由 AdminAuthInterceptor 用独立令牌校验）

        // 管理端：校验 X-Admin-Token，不走用户登录态
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/admin/**");
    }


}
