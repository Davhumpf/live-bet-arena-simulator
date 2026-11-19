package edu.arena.bettingapi;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component
public class DemoUsers implements ApplicationRunner {
    private final Store store; private final PasswordEncoder passwords; private final String demo; private final String admin;
    public DemoUsers(Store store,PasswordEncoder passwords,@Value("${arena.demo-password}") String demo,@Value("${arena.admin-password}") String admin) {
        this.store=store; this.passwords=passwords; this.demo=demo; this.admin=admin;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (demo.length()<10 || admin.length()<10) throw new IllegalArgumentException("Las contraseñas demo/admin requieren al menos 10 caracteres");
        create("alex@arena.test","Alex Rivera","USER",demo);
        create("sam@arena.test","Sam Torres","USER",demo);
        create("admin@arena.test","Administrador","ADMIN",admin);
    }
    private void create(String email,String name,String role,String password) {
        if (store.byEmail(email)==null) store.save(new Account(UUID.nameUUIDFromBytes(email.getBytes(java.nio.charset.StandardCharsets.UTF_8)),email,name,passwords.encode(password),role));
    }
}
