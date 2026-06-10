package com.pfe.gateway.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto2 {
    private String username;
    private String email;
    private String telephone;  // <- nouveau champ
}