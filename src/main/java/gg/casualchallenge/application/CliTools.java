package gg.casualchallenge.application;

import gg.casualchallenge.application.api.security.JwtService;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class CliTools {

    public static void main(String[] args) throws IOException {
        // TODO nice setup with Spring (Boot) CLI?
        if (args.length < 1) {
            System.err.println("Please provide a command parameter.");
            return;
        }

        String command = args[0];
        switch (command) {
            case "generate-secret-key": {
                SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
                System.out.println(new String(secretKey.getEncoded(), StandardCharsets.UTF_8));
                System.out.println(Base64.getEncoder().encodeToString(secretKey.getEncoded()));
                break;
            }
            case "generate-jwt": {
                if (args.length < 2) {
                    System.err.println("Please provide a name that's used as jwt username as 2nd parameter, e.g. 'discord-bot'.");
                    return;
                }

                // Dynamically resolve the project root directory
                Path projectRoot = Paths.get("").toAbsolutePath();
                Path secretFile = projectRoot.resolve("jwt_private_key.txt");
                String secretKey = Files.readString(secretFile).trim();

                JwtService jwtService = new JwtService(secretKey);
                String token = jwtService.generateToken(args[1]);
                System.out.println("Token: " + token);
                System.out.println(jwtService.extractClaims(token));
                System.out.println(jwtService.validateToken(token));
                break;
            }
        }



    }

}
