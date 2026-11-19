package edu.arena.common;
import edu.arena.common.persistence.Store;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
@Configuration
public class Security {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(Store store) {
        return email -> {
            var user=store.byEmail(email);
            if (user==null) throw new UsernameNotFoundException("Usuario desconocido");
            return User.withUsername(user.email).password(user.password).roles(user.role).build();
        };
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/","/index.html","/dashboard.html","/favicon.ico","/error","/css/**","/js/**","/ws").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .httpBasic(b->b.realmName("Live Bet Arena")).build();
    }
}
