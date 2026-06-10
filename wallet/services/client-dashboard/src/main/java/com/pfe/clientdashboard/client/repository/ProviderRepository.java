package com.pfe.clientdashboard.client.repository;


import com.pfe.clientdashboard.provider.entities.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, String> {

    //Recherche tous les providers dont le providerCode correspond exactement à la valeur fournie.
    List<Provider> findByProviderCode(String providerCode);

    //Recherche tous les providers dont le nom contient la chaîne fournie (insensible à la casse)
    List<Provider> findByProviderNameContainingIgnoreCase(String providerName);

    //Vérifie si un provider existe avec le code fourni
    boolean existsByProviderCode(String providerCode);
}
