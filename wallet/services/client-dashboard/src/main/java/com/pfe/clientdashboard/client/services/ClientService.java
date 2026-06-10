package com.pfe.clientdashboard.client.services;


import com.pfe.clientdashboard.client.dtos.ClientDTO;

import java.util.List;

public interface ClientService {
    List<ClientDTO> getAllClients();
    List<ClientDTO> searchClients(String search);
    ClientDTO getClientById(String id);
}
