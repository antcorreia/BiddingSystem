package com.projetobd.Projeto.BD;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.sql.*;
import org.slf4j.*;

@SpringBootApplication
public class RestAPI {
	private static final Logger logger = LoggerFactory.getLogger(RestAPI.class);

	public static void main(String[] args) {
		SpringApplication.run(RestAPI.class, args);
	}

	public static Connection getConnection() {
		try {
			return DriverManager.getConnection("jdbc:postgresql://localhost:5432/projeto", "postgres", "postgres");
		} catch (Exception e) {
			logger.error("Erro a ligar a base de dados: ", e);
		}

		System.exit(0);
		return null;
	}
}
