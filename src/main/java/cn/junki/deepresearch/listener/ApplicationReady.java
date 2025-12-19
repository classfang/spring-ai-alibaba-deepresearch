package cn.junki.deepresearch.listener;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 应用就绪监听
 *
 * @author Junki
 * @since 2025-12-19
 */
@Configuration
public class ApplicationReady {

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String contextPath = environment.getProperty("server.servlet.context-path", "");

            System.out.printf("""
                    🎉========================================🎉
                    ✅ 应用启动成功！
                    🚀 访问对话界面: http://localhost:%s%s/chatui/index.html
                    🎉========================================🎉
                    """, port, contextPath);
        };
    }

}
