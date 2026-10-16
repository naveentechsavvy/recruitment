package com.example.recruitment.service;

import com.example.recruitment.dto.ClientProcessRequest;
import com.example.recruitment.dto.ClientProcessResponse;
import com.example.recruitment.dto.ClientRequest;
import com.example.recruitment.dto.ClientResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ClientService {
    ClientResponse createClient(ClientRequest request);
    List<ClientResponse> getAllClients();
    ClientResponse getClient(Long id);
    ClientResponse updateClient(Long id, ClientRequest request);
    void deleteClient(Long id);

    /** Freeze: keeps all data, but the portal takes no new applications or changes. */
    ClientResponse freezeClient(Long id, String byEmail);
    ClientResponse unfreezeClient(Long id);

    ClientResponse uploadLogo(Long id, MultipartFile file);
    void removeLogo(Long id);

    List<ClientProcessResponse> configureProcesses(Long clientId, ClientProcessRequest request);
    List<ClientProcessResponse> getClientProcesses(Long clientId);
}
