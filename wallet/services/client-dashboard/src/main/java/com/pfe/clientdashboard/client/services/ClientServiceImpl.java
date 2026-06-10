package com.pfe.clientdashboard.client.services;



import com.pfe.clientdashboard.client.dtos.ClientDTO;
import com.pfe.clientdashboard.client.repository.ClientRepository;
import com.pfe.clientdashboard.client.entities.Client;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;

    public ClientServiceImpl(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClientDTO> searchClients(String search) {
        return clientRepository.searchClients(search)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ClientServiceImpl.java
    @Override
    public ClientDTO getClientById(String id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        return toDTO(client);
    }

    private ClientDTO toDTO(Client client) {
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setFirstName(client.getFirstName());
        dto.setLastName(client.getLastName());
        dto.setPhoneNumber(client.getPhoneNumber());
        dto.setCreateDateTime(client.getCreateDateTime());
        return dto;
    }
}