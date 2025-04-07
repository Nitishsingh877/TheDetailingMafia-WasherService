package com.thederailingmafia.carwash.washerservice.service;


import com.thederailingmafia.carwash.washerservice.model.UserModel;
import com.thederailingmafia.carwash.washerservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserModel user = userRepository.findByEmail(email).orElseThrow(() ->  new UsernameNotFoundException("USer not " +
                "found "));
        return new org.springframework.security.core.userdetails.User(user.getEmail(),user.getPassword() !=null ?
                user.getPassword(): "",new ArrayList<>());
    }
}


