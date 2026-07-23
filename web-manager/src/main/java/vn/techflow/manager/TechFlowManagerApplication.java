package vn.techflow.manager;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
@EnableAsync @SpringBootApplication
public class TechFlowManagerApplication {
 public static void main(String[] args){SpringApplication.run(TechFlowManagerApplication.class,args);}
 @Bean OpenAPI api(){return new OpenAPI().info(new Info().title("AI TechFlow Manager API").version("1.0").description("Quản lý công việc và chạy pipeline video nháp cần kiểm duyệt."));}
}
