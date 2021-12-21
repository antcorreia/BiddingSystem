package com.projetobd.Projeto.BD;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtUtil {
    private static final String SECRET_KEY = "secret";

    public static String extractUsername(String token){ return extractClaim(token, Claims::getSubject); }

    public static Date extractExpiration(String token){ return extractClaim(token, Claims::getExpiration); }

    public static <T> T extractClaim(String token, Function<Claims, T> claimsResolver){ return claimsResolver.apply(extractAllClaims(token)); }
    private static Claims extractAllClaims(String token){ return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody(); }

    public static String generateToken(String username){ return createToken(new HashMap<>(), username); }
    private static String createToken(Map<String, Object> claims, String subject){ return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis())).setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)).signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact(); }

    public static Boolean validateToken(String token, String username){ return (username.equals(extractUsername(token)) && !isTokenExpired(token)); }
    private static Boolean isTokenExpired(String token){ return extractExpiration(token).before(new Date()); }
}
