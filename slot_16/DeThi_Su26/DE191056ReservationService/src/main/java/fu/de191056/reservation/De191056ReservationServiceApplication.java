package fu.de191056.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class De191056ReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(De191056ReservationServiceApplication.class, args);
    }

}
