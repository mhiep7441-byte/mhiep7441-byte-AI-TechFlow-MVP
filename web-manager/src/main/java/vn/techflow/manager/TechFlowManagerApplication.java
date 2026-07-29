package vn.techflow.manager;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import vn.techflow.manager.youtube.YouTubeConfiguration;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableConfigurationProperties(YouTubeConfiguration.class)
public class TechFlowManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TechFlowManagerApplication.class, args);
    }

    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info()
                .title("AI TechFlow Manager API")
                .version("1.2")
                .description("Quản lý user, campaign, video AI và upload có bước kiểm duyệt."));
    }
}
