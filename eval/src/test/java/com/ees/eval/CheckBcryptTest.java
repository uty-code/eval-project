package com.ees.eval;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CheckBcryptTest {

    @Test
    public void testBcrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde";
        
        System.out.println("=========================================");
        for (String pw : new String[]{"1001", "1002", "1234", "admin123"}) {
            boolean match = encoder.matches(pw, hash);
            System.out.println("Password '" + pw + "': " + match);
        }
        System.out.println("=========================================");
    }
}
