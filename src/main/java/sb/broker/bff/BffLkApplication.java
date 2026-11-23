package sb.broker.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BffLkApplication {

    static void main(String[] args) {
        SpringApplication.run(BffLkApplication.class, args);
    }
}
