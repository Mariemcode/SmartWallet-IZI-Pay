package com.pfe.clientdashboard.client.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    private String id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDateTime createDateTime;

}
