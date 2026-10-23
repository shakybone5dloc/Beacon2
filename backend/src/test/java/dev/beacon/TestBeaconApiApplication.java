package dev.beacon;

import org.springframework.boot.SpringApplication;

public class TestBeaconApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(BeaconApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
