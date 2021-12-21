package com.projetobd.Projeto.BD;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;
import org.slf4j.*;

import javax.servlet.http.HttpServletResponse;

@RestController
public class Login {
    private static final Logger logger = LoggerFactory.getLogger(Login.class);

    /***
     * Menu de boasvindas
     * http://localhost:8080/dbproj/bemvindo/
     */
    @GetMapping("/bemvindo/")
    public String bemvindo() {
        return """
                Bem vindo!
                Para se registar ou fazer login use http://localhost:8080/dbproj/user/
                Copie e cole o token recebido na seccao 'Authorization', em 'Bearer Token'
                Antonio Correia e Pedro Martins
                """;
    }

    /***
     * Registar uma conta nova
     * http://localhost:8080/dbproj/leiloes/atualizar/
     */
    @PostMapping(value="/user/", consumes="application/json")
    @ResponseBody
    public String registarUtilizador(@RequestBody Map<String, Object> payload){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            PreparedStatement ps = connection.prepareStatement("INSERT INTO utilizadores (id, username, email, password) VALUES (?, ?, ?, ?)");
            ResultSet testeEmail = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE email = '%s'", payload.get("email")));
            ResultSet testeUsername = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", payload.get("username")));
            ResultSet maxID = connection.createStatement().executeQuery("SELECT MAX(id) FROM utilizadores");
            if (testeEmail.next())
                return "Email ja registado!";
            if (testeUsername.next())
                return "Username ja esta a ser usado!";
            maxID.next();


            int id = maxID.getInt("max");
            ps.setInt(1, ++id);
            ps.setString(2, (String) payload.get("username"));
            ps.setString(3, (String) payload.get("email"));
            ps.setString(4, new BCryptPasswordEncoder().encode((String) payload.get("password")));

            int affectedRows = ps.executeUpdate();
            connection.commit();
            if (affectedRows == 1)
                return "Registado com sucesso!";
        }
        catch (SQLException e){
            logger.error("Erro na base de dados: ", e);
            try{
                connection.rollback();
            }
            catch (SQLException e1){
                logger.warn("Erro a tentar dar rollback: ", e1);
            }
            finally{
                try{
                    connection.close();
                }
                catch (SQLException ex2){
                    logger.error("Erro na base de dados: ", ex2);
                }
            }
        }

        return "Nao foi possivel concluir o registo!";
    }

    /***
     * Fazer login para receber o token de autenticacao
     * http://localhost:8080/dbproj/user/
     */
    @PutMapping(value="/user/", consumes="application/json")
    @ResponseBody
    public String loginUtilizador(@RequestBody Map<String, Object> payload, HttpServletResponse response) {
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            ResultSet testeUsername = connection.createStatement().executeQuery(String.format("SELECT username, password FROM utilizadores WHERE username = '%s'", payload.get("username")));
            if (testeUsername.next()){
                if (new BCryptPasswordEncoder().matches((String) payload.get("password"), testeUsername.getString("password"))){
                    String token = JwtUtil.generateToken((String) payload.get("username"));

                    return String.format("Login efetuado com sucesso! O seu token e '%s'", token);
                }
                else
                    return "Password incorreta!";
            }
        } catch (SQLException e) {
            logger.error("Erro na base de dados: ", e);
            try {
                connection.rollback();
            } catch (SQLException e1) {
                logger.warn("Erro a tentar dar rollback: ", e1);
            } finally {
                try {
                    connection.close();
                } catch (SQLException ex2) {
                    logger.error("Erro na base de dados: ", ex2);
                }
            }
        }

        return "Utilizador nao registado!";
    }
}
