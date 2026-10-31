package fu.de191056.postservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients // bật quét các @FeignClient (OpenFeign)
public class De191056PostServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(De191056PostServiceApplication.class, args);
    }

}
