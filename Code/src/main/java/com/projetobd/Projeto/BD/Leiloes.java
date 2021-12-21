package com.projetobd.Projeto.BD;

import org.springframework.web.bind.annotation.*;
import org.slf4j.*;
import java.sql.*;
import java.util.*;

@RestController
public class Leiloes {
    private static final Logger logger = LoggerFactory.getLogger(Leiloes.class);

    /***
     * Criar um leilao
     * http://localhost:8080/dbproj/leilao/*
     * Recebe JSON:
     * {
     *     "artigoId": "String",
     *     "precoMinimo": "Float",
     *     "titulo": "String",
     *     "descricao": "String",
     *     "dataFinal": "YY-MM-DD HH:MM:SS"
     * }
     */
    @PostMapping(value="/leilao/", consumes="application/json")
    public String criarLeilao(@RequestHeader("Authorization") String authentication, @RequestBody Map<String, Object> payload){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else
                return "Nao adicionou nenhum Bearer token!";

            String username;
            try {
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e) {
                return "Token invalido, faca login para verificar o seu token!";
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                return "Token expirado, faca login novamente para ter um novo token!";
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username, id FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username")))
                return "Token invalido, faca login para verificar o seu token!";

            PreparedStatement ps = connection.prepareStatement("INSERT INTO leiloes (id, iduser, titulo, isbnartigo, descricao, data, versao, ativo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ResultSet testeISBN = connection.createStatement().executeQuery(String.format("SELECT isbn FROM artigos WHERE isbn = '%s'", payload.get("artigoId")));
            ResultSet maxIDleilao = connection.createStatement().executeQuery("SELECT MAX(id) FROM leiloes");
            ResultSet maxIDlicitacao = connection.createStatement().executeQuery("SELECT MAX(id) FROM licitacoes");
            maxIDleilao.next();
            maxIDlicitacao.next();
            if(!testeISBN.next())
                return "Nao existe nenhum artigo com esse ID";
            try{
                Float.parseFloat((String) payload.get("precoMinimo"));
            } catch(NumberFormatException e){
                return "Preco minimo mal formatado!";
            }
            try{
                Timestamp.valueOf((String) payload.get("dataFinal"));
            } catch (IllegalArgumentException e){
                return "Data para o fim do leilao mal estruturada!";
            }

            if (new Timestamp(System.currentTimeMillis()).compareTo(Timestamp.valueOf((String) payload.get("dataFinal"))) >= 0)
                return "A data que indicou como data final ja passou!";

            int idLicitacao = maxIDlicitacao.getInt("max");
            int idUser = verificaToken.getInt("id");
            int idLeilao = maxIDleilao.getInt("max");
            connection.createStatement().executeUpdate(String.format("INSERT INTO licitacoes (id, iduser, idleilao, preco) VALUES (%d, %d, %d, %.2f)", ++idLicitacao, idUser, ++idLeilao, Float.parseFloat((String) payload.get("precoMinimo"))));
            ps.setInt(1, idLeilao);
            ps.setInt(2, idUser);
            ps.setString(3, (String) payload.get("titulo"));
            ps.setString(4, (String) payload.get("artigoId"));
            ps.setString(5, (String) payload.get("descricao"));
            ps.setTimestamp(6, Timestamp.valueOf((String) payload.get("dataFinal")));
            ps.setInt(7 ,1);
            ps.setBoolean(8, true);

            int affectedRows = ps.executeUpdate();
            connection.commit();
            if (affectedRows == 1)
                return "Leilao criado!";

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

        return null;
    }

    /***
     * Apresentar todos os leiloes
     * http://localhost:8080/dbproj/leiloes/
     */
    @GetMapping("/leiloes/")
    public StringBuilder devolveLeiloes(@RequestHeader("Authorization") String authentication) {
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            StringBuilder string = new StringBuilder();
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else{
                string.append("Nao adicionou nenhum Bearer token!");

                return string;
            }

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                string.append("Token expirado, faca login novamente para ter um novo token!");

                return string;
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username"))){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            }

            ResultSet leiloes = connection.createStatement().executeQuery("SELECT * FROM leiloes WHERE ativo = true AND (id, versao) IN (SELECT id, MAX(versao) FROM leiloes GROUP BY id)");
            ResultSet nome;
            ResultSet artigo;
            ResultSet preco;
            while(leiloes.next()){
                nome = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE id = %s", leiloes.getInt("iduser")));
                artigo = connection.createStatement().executeQuery(String.format("SELECT nome FROM artigos WHERE isbn = '%s'", leiloes.getString("isbnartigo")));
                preco = connection.createStatement().executeQuery(String.format("SELECT MAX(preco) FROM licitacoes GROUP BY idleilao HAVING idleilao = %s", leiloes.getInt("id")));
                preco.next();
                nome.next();
                artigo.next();
                string.append(String.format("Titulo: %s\nArtigo: %s\nDono: %s\nData: %s\nPreco Atual: %.2f$\nDescricao: %s\n\n", leiloes.getString("titulo"), artigo.getString("nome"), nome.getString("username"), leiloes.getDate("data"), preco.getFloat("max"),leiloes.getString("descricao")));
            }

            return string;
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

        return null;
    }

    /***
     * Apresentar todos os leiloes com uma certa keyword
     * http://localhost:8080/dbproj/leiloes/{keyword}/
     */
    @GetMapping("/leiloes/{keyword}/")
    public StringBuilder devolveCertosLeiloes(@RequestHeader("Authorization") String authentication, @PathVariable("keyword") String keyword) {
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            StringBuilder string = new StringBuilder();
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else{
                string.append("Nao adicionou nenhum Bearer token!");

                return string;
            }

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                string.append("Token expirado, faca login novamente para ter um novo token!");

                return string;
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username"))){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            }
            ResultSet leiloes = connection.createStatement().executeQuery(String.format("SELECT id FROM leiloes WHERE ativo = true AND (isbnartigo = '%s' OR descricao LIKE '%%%s%%')", keyword, keyword));
            if (leiloes.next()) {
                string.append("Os ID's dos leiloes com esse artigo sao:\n");
                do {
                    string.append(String.format("%d\n", leiloes.getInt("id")));
                } while (leiloes.next());
            }
            else {
                string.append("Nenhum leilao com esse artigo ou descricao encontrados!");

                return string;
            }

            return string;
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

        return null;
    }

    /***
     * Apresentar um leilao especifico
     * http://localhost:8080/dbproj/leilao/
     */
    @GetMapping("/leilao/{leilaoid}/")
    public StringBuilder devolveLeilao(@RequestHeader("Authorization") String authentication, @PathVariable("leilaoid") String leilaoid) {
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            StringBuilder string = new StringBuilder();
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else{
                string.append("Nao adicionou nenhum Bearer token!");

                return string;
            }

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                string.append("Token expirado, faca login novamente para ter um novo token!");

                return string;
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username"))){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            }

            ResultSet leilao;
            try{
                Integer.parseInt(leilaoid);
                leilao = connection.createStatement().executeQuery(String.format("SELECT * FROM leiloes WHERE id = %s AND (id, versao) IN (SELECT id, MAX(versao) FROM leiloes GROUP BY id)", leilaoid));
            }
            catch (NumberFormatException e){
                string.append("ID nao e um numero!");

                return string;
            }
            if (leilao.next()) {
                ResultSet nome = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE id = %s", leilao.getInt("iduser")));
                ResultSet preco = connection.createStatement().executeQuery(String.format("SELECT MAX(preco) FROM licitacoes GROUP BY idleilao HAVING idleilao = %s", leilaoid));
                ResultSet licitacoes = connection.createStatement().executeQuery(String.format("SELECT iduser, preco FROM licitacoes WHERE idleilao = %s", leilaoid));
                preco.next();
                nome.next();
                string.append(String.format("Titulo: %s\nDono: %s\nData: %s\nLicitacoes:\n", leilao.getString("titulo"), nome.getString("username"), leilao.getDate("data")));
                while (licitacoes.next())
                    string.append(String.format("%.2f$\n", licitacoes.getFloat("preco")));
                string.append(String.format("Descricao: %s\n\nMensagens:\n", leilao.getString("descricao")));

                ResultSet mensagens = connection.createStatement().executeQuery(String.format("SELECT * from mensagens WHERE idleilao = '%s'", leilaoid));
                if (mensagens.next())
                    do{
                        nome = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE id = %s", mensagens.getInt("iduser")));
                        nome.next();
                        string.append(String.format("Utilizador: %s\nData: %s\n%s\n\n", nome.getString("username"), mensagens.getDate("datamensagem"), mensagens.getString("mensagem")));
                    }while(mensagens.next());
                else
                    string.append("Nao ha mensagens para este leilao");
            }
            else {
                string.append("Nao existe nenhum leilao com esse id!");

                return string;
            }

            return string;
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

        return null;
    }

    /***
     * Apresentar uma versao especifica de um leilao especifico
     * http://localhost:8080/dbproj/leilao/{versao}/
     */
    @GetMapping("/leilao/{leilaoid}/{versao}")
    public StringBuilder devolveLeilaoVersao(@RequestHeader("Authorization") String authentication, @PathVariable("leilaoid") String leilaoid, @PathVariable("versao") String versao) {
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            StringBuilder string = new StringBuilder();
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else{
                string.append("Nao adicionou nenhum Bearer token!");

                return string;
            }

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                string.append("Token expirado, faca login novamente para ter um novo token!");

                return string;
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username"))){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            }

            ResultSet leilao;
            try{
                Integer.parseInt(leilaoid);
                leilao = connection.createStatement().executeQuery(String.format("SELECT * FROM leiloes WHERE id = %s", leilaoid));
                if (!leilao.next()) {
                    string.append("Nao existe um leilao com esse ID!");

                    return string;
                }
            }
            catch (NumberFormatException e){
                string.append("ID nao e um numero!");

                return string;
            }

            try{
                Integer.parseInt(versao);
                leilao = connection.createStatement().executeQuery(String.format("SELECT * FROM leiloes WHERE id = %s AND versao = %s", leilaoid, versao));
            }
            catch (NumberFormatException e){
                string.append("Versao nao e um numero!");

                return string;
            }

            if (leilao.next()) {
                ResultSet nome = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE id = %s", leilao.getInt("iduser")));
                ResultSet preco = connection.createStatement().executeQuery(String.format("SELECT MAX(preco) FROM licitacoes GROUP BY idleilao HAVING idleilao = %s", leilaoid));
                ResultSet licitacoes = connection.createStatement().executeQuery(String.format("SELECT iduser, preco FROM licitacoes WHERE idleilao = %s", leilaoid));
                preco.next();
                nome.next();
                string.append(String.format("Titulo: %s\nDono: %s\nData: %s\nLicitacoes:\n", leilao.getString("titulo"), nome.getString("username"), leilao.getDate("data")));
                while (licitacoes.next())
                    string.append(String.format("%.2f$\n", licitacoes.getFloat("preco")));
                string.append(String.format("Descricao: %s\n\nMensagens:\n", leilao.getString("descricao")));

                ResultSet mensagens = connection.createStatement().executeQuery(String.format("SELECT * from mensagens WHERE idleilao = '%s'", leilaoid));
                if (mensagens.next())
                    do{
                        nome = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE id = %s", mensagens.getInt("iduser")));
                        nome.next();
                        string.append(String.format("Utilizador: %s\nData: %s\n%s\n\n", nome.getString("username"), mensagens.getDate("datamensagem"), mensagens.getString("mensagem")));
                    }while(mensagens.next());
                else
                    string.append("Nao ha mensagens para este leilao");
            }
            else {
                string.append("Nao existe essa versao do leilao indicado!");

                return string;
            }

            return string;
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

        return null;
    }

    /***
     * Apresentar os leiloes nos quais teve atividade
     * http://localhost:8080/dbproj/leiloes/meusleiloes/
     */
    @GetMapping("/leiloes/meusleiloes/")
    public StringBuilder meusLeiloes(@RequestHeader("Authorization") String authentication){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            StringBuilder string = new StringBuilder();
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else{
                string.append("Nao adicionou nenhum Bearer token!");

                return string;
            }

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                string.append("Token expirado, faca login novamente para ter um novo token!");

                return string;
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username, id FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username"))){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            }
            ResultSet leiloes = connection.createStatement().executeQuery(String.format("SELECT DISTINCT idleilao FROM licitacoes WHERE iduser = %d", verificaToken.getInt("id")));
            if (leiloes.next()) {
                string.append("Os ID's dos leiloes nos quais teve atividade sao:\n");
                do {
                    string.append(String.format("%d\n", leiloes.getInt("idleilao")));
                } while (leiloes.next());
            }
            else {
                string.append("Nao tem atividade em nenhum leilao!");

                return string;
            }

            return string;
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

        return null;
    }

    /***
     * Fazer uma licitacao num leilao
     * http://localhost:8080/dbproj/licitar/{leilaoid}/
     * Recebe JSON:
     * {
     *      "licitacao": "Float"
     * }
     */
    @PostMapping(value="/licitar/{leilaoid}/", consumes="application/json")
    public String licitar(@RequestHeader("Authorization") String authentication,  @PathVariable("leilaoid") String leilaoid, @RequestBody Map<String, Object> payload){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else
                return "Nao adicionou nenhum Bearer token!";

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                return "Token invalido, faca login para verificar o seu token!";
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                return "Token expirado, faca login novamente para ter um novo token!";
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username, id FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username")))
                return "Token invalido, faca login para verificar o seu token!";

            try{
                Integer.parseInt(leilaoid);
            } catch (NumberFormatException e){
                return "ID nao e um numero";
            }

            ResultSet preco = connection.createStatement().executeQuery(String.format("SELECT MAX(preco) FROM licitacoes GROUP BY idleilao HAVING idleilao = %s", leilaoid));
            ResultSet maxID = connection.createStatement().executeQuery("SELECT MAX(id) FROM licitacoes");
            ResultSet idNotificacao = connection.createStatement().executeQuery("SELECT MAX(id) FROM notificacoes");
            ResultSet tempo = connection.createStatement().executeQuery(String.format("SELECT data FROM leiloes WHERE id = %s", leilaoid));
            ResultSet idMaxBidder = connection.createStatement().executeQuery(String.format("SELECT iduser FROM licitacoes WHERE idleilao = %s AND (idleilao, preco) IN (SELECT idleilao, MAX(preco) FROM licitacoes GROUP BY idleilao)", leilaoid));
            if (!preco.next())
                return "Nao existe um leilao com esse ID";
            maxID.next();
            tempo.next();
            idMaxBidder.next();
            idNotificacao.next();

            if (new Timestamp(System.currentTimeMillis()).compareTo(tempo.getTimestamp("data")) >= 0)
                return "Esse leilao ja se encontra encerrado!";

            try{
                if (Float.parseFloat((String) payload.get("licitacao")) <= preco.getFloat("max"))
                    return "Ja existe uma licitacao maior ou igual a que tentou fazer!";
            } catch(NumberFormatException e){
                return "Licitacao mal formatada!";
            }

            PreparedStatement ps1 = connection.prepareStatement("INSERT INTO licitacoes (id, iduser, idleilao, preco) VALUES (?, ?, ?, ?)");
            PreparedStatement ps2 = connection.prepareStatement("INSERT INTO notificacoes (id, iduser, mensagem, lida) VALUES (?, ?, ?, false)");
            ps1.setInt(1, 1 + maxID.getInt("max"));
            ps1.setInt(2, verificaToken.getInt("id"));
            ps1.setInt(3, Integer.parseInt(leilaoid));
            ps1.setFloat(4, Float.parseFloat((String) payload.get("licitacao")));

            ps2.setInt(1, 1 + idNotificacao.getInt("max"));
            ps2.setInt(2, idMaxBidder.getInt("iduser"));
            ps2.setString(3, String.format("A sua licitacao no leilao com o ID %s foi ultrapassada", leilaoid));

            int affectedRows = ps1.executeUpdate();
            ps2.executeUpdate();
            connection.commit();
            if (affectedRows == 1)
                return "Licitacao adicionada!";

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

        return null;
    }

    /***
     * Editar as propriedades de um leilao
     * http://localhost:8080/dbproj/leilao/{leilaoid}/
     * {
     *     "novoTitulo": "String",
     *     "novaDescricao": "String"
     * }
     */
    @PutMapping(value="/leilao/{leilaoid}/", consumes="application/json")
    public String editarLeilao(@RequestHeader("Authorization") String authentication,  @PathVariable("leilaoid") String leilaoid, @RequestBody Map<String, Object> payload){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else
                return "Nao adicionou nenhum Bearer token!";

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                return "Token invalido, faca login para verificar o seu token!";
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                return "Token expirado, faca login novamente para ter um novo token!";
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username")))
                return "Token invalido, faca login para verificar o seu token!";

            ResultSet leilao;
            try{
                Integer.parseInt(leilaoid);
                leilao = connection.createStatement().executeQuery(String.format("SELECT * FROM leiloes WHERE id = %s", leilaoid));
            } catch (NumberFormatException e){
                return "ID nao e um numero!";
            }

            if (!leilao.next())
                return "Nao existe um leilao com esse ID";

            ResultSet UserID = connection.createStatement().executeQuery(String.format("SELECT id FROM utilizadores WHERE username = '%s'", username));
            ResultSet preco = connection.createStatement().executeQuery(String.format("SELECT MAX(preco) FROM licitacoes GROUP BY idleilao HAVING idleilao = %s", leilaoid));
            ResultSet tempo = connection.createStatement().executeQuery(String.format("SELECT data FROM leiloes WHERE id = %s", leilaoid));
            ResultSet artigo = connection.createStatement().executeQuery(String.format("SELECT isbnartigo FROM leiloes WHERE id = %s", leilaoid));
            ResultSet maxVersao = connection.createStatement().executeQuery(String.format("SELECT MAX(versao) FROM leiloes GROUP BY id HAVING id = %s", leilaoid));
            artigo.next();
            UserID.next();
            preco.next();
            tempo.next();
            maxVersao.next();

            if (new Timestamp(System.currentTimeMillis()).compareTo(tempo.getTimestamp("data")) >= 0)
                return "Esse leilao ja se encontra encerrado!";

            int versao = maxVersao.getInt("max");
            PreparedStatement ps = connection.prepareStatement(String.format("UPDATE leiloes SET ativo = false WHERE id = %s AND versao = %d", leilaoid, versao));
            ps.executeUpdate();

            ps = connection.prepareStatement("INSERT INTO leiloes (id, iduser, titulo, isbnartigo, descricao, data, versao, ativo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            int idUser = UserID.getInt("id");
            ps.setInt(1,  Integer.parseInt(leilaoid));
            ps.setInt(2, idUser);
            ps.setString(3, (String) payload.get("novoTitulo"));
            ps.setString(4, artigo.getString("isbnartigo"));
            ps.setString(5, (String) payload.get("novaDescricao"));
            ps.setTimestamp(6, tempo.getTimestamp("data"));
            ps.setInt(7 ,++versao);
            ps.setBoolean(8, true);

            int affectedRows = ps.executeUpdate();
            connection.commit();
            if (affectedRows == 1)
                return "Leilao atualizado!";

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

        return null;
    }

    /***
     * Escrever uma mensagem no moral de um leilao
     * http://localhost:8080/dbproj/mensagem/{leilaoid}/
     * {
     *     "mensagem": "String"
     * }
     */
    @PostMapping(value="/mensagem/{leilaoid}/", consumes="application/json")
    public String escreverMensagem(@RequestHeader("Authorization") String authentication,  @PathVariable("leilaoid") String leilaoid, @RequestBody Map<String, Object> payload){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else
                return "Nao adicionou nenhum Bearer token!";

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                return "Token invalido, faca login para verificar o seu token!";
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                return "Token expirado, faca login novamente para ter um novo token!";
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username, id FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username")))
                return "Token invalido, faca login para verificar o seu token!";

            ResultSet leilao;
            try{
                Integer.parseInt(leilaoid);
                leilao = connection.createStatement().executeQuery(String.format("SELECT * FROM leiloes WHERE id = %s", leilaoid));
            } catch (NumberFormatException e){
                return "ID nao e um numero!";
            }

            if (!leilao.next())
                return "Nao existe um leilao com esse ID";

            PreparedStatement ps1 = connection.prepareStatement("INSERT INTO mensagens (id, idleilao, iduser, mensagem, datamensagem) VALUES (?, ?, ?, ?, ?)");
            PreparedStatement ps2 = connection.prepareStatement("INSERT INTO notificacoes (id, iduser, mensagem, lida) VALUES (?, ?, ?, false)");
            ResultSet idMensagem = connection.createStatement().executeQuery("SELECT MAX(id) FROM mensagens");
            ResultSet idNotificacao = connection.createStatement().executeQuery("SELECT MAX(id) FROM notificacoes");
            ResultSet userID = connection.createStatement().executeQuery(String.format("SELECT iduser FROM leiloes WHERE id = %s", leilaoid));
            userID.next();
            idNotificacao.next();
            idMensagem.next();
            int mensagemid = idMensagem.getInt("max");
            int idUser = verificaToken.getInt("id");


            ps1.setInt(1, ++mensagemid);
            ps1.setInt(2, Integer.parseInt(leilaoid));
            ps1.setInt(3, idUser);
            ps1.setString(4, (String) payload.get("mensagem"));
            ps1.setTimestamp(5, new Timestamp(System.currentTimeMillis()));

            ps2.setInt(1, 1 + idNotificacao.getInt("max"));
            ps2.setInt(2, userID.getInt("iduser"));
            ps2.setString(3, String.format("Nova mensagem no leilao com o ID: %s", leilaoid));

            int affectedRows = ps1.executeUpdate();
            ps2.executeUpdate();
            connection.commit();
            if (affectedRows == 1)
                return "Mensagem adicionada!";

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

        return null;
    }

    /***
     * Ler notificacoes
     * http://localhost:8080/dbproj/notificacoes/
     */
    @GetMapping("/notificacoes/")
    public StringBuilder lerNotificacoes(@RequestHeader("Authorization") String authentication){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            StringBuilder string = new StringBuilder();
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else{
                string.append("Nao adicionou nenhum Bearer token!");

                return string;
            }

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                string.append("Token expirado, faca login novamente para ter um novo token!");

                return string;
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username, id FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username"))){
                string.append("Token invalido, faca login para verificar o seu token!");

                return string;
            }
            ResultSet leiloes = connection.createStatement().executeQuery(String.format("SELECT mensagem FROM notificacoes WHERE iduser = %d AND lida = false", verificaToken.getInt("id")));
            if (leiloes.next()) {
                string.append("As suas notificacoes:\n");
                do {
                    string.append(String.format("%s\n", leiloes.getString("mensagem")));
                } while (leiloes.next());
            }
            else {
                string.append("Nao tem notificacoes por ler!");

                return string;
            }

            connection.createStatement().executeUpdate(String.format("UPDATE notificacoes SET lida = true WHERE iduser = %d", verificaToken.getInt("id")));
            connection.commit();

            return string;
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

        return null;
    }

    /***
     * Atualizar o estado dos leiloes
     * http://localhost:8080/dbproj/leiloes/atualizar/
     */
    @GetMapping("/leiloes/atualizar/")
    public String atualizaLeiloes(@RequestHeader("Authorization") String authentication){
        Connection connection = RestAPI.getConnection();

        try {
            connection.setAutoCommit(false);
            String token;
            if (authentication != null && authentication.startsWith("Bearer"))
                token = authentication.substring(7);
            else
                return "Nao adicionou nenhum Bearer token!";

            String username;
            try{
                username = JwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.SignatureException e){
                return "Token invalido, faca login para verificar o seu token!";
            } catch (io.jsonwebtoken.ExpiredJwtException e){
                return "Token expirado, faca login novamente para ter um novo token!";
            }
            ResultSet verificaToken = connection.createStatement().executeQuery(String.format("SELECT username FROM utilizadores WHERE username = '%s'", username));
            if (!verificaToken.next() || !JwtUtil.validateToken(token, verificaToken.getString("username")))
                return "Token invalido, faca login para verificar o seu token!";

            PreparedStatement ps = connection.prepareStatement(String.format("UPDATE leiloes SET ativo = false WHERE ativo = true AND data < '%s'", new Timestamp(System.currentTimeMillis())));
            int affectedRows = ps.executeUpdate();
            connection.commit();
            if (affectedRows == 0)
                return "Todos os leiloes estavam atualizados!";
            else
                return "Leiloes atualziados!";
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

        return null;
    }
}
