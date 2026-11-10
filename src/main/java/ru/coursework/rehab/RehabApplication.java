package ru.coursework.rehab;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
@SpringBootApplication
public class RehabApplication {
    public static void main(String[] args) { SpringApplication.run(RehabApplication.class,args); }
    @Bean Clock clock(@Value("${app.zone}") String zone) { return Clock.system(ZoneId.of(zone)); }
}
