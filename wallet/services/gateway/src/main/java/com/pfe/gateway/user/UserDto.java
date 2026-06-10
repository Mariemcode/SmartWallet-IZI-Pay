package com.pfe.gateway.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto {
    private String username;   // numéro de téléphone
    private String email;
    private String password;
    private String firstName;
    private String lastName;
}
