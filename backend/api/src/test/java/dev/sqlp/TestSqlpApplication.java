package dev.sqlp;

import org.springframework.boot.SpringApplication;

public class TestSqlpApplication {

	public static void main(String[] args) {
		SpringApplication.from(SqlpApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
