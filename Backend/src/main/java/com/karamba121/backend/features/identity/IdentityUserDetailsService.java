package com.karamba121.backend.features.identity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserDetailsService implements UserDetailsService {

    private final IdentityUserRepository users;

    public IdentityUserDetailsService(IdentityUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        IdentityUser user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .disabled(!user.isEnabled() || !user.isEmailVerified())
                .build();
    }
}
