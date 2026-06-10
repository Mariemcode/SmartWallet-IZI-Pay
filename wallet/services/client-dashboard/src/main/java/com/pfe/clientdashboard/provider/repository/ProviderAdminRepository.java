package com.pfe.clientdashboard.provider.repository;

import com.pfe.clientdashboard.provider.entities.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderAdminRepository extends JpaRepository<Provider, String> {

    //Recherche tous les providers dont le providerCode correspond exactement à la valeur fournie.
    List<Provider> findByProviderCode(String providerCode);

    //Recherche tous les providers dont le nom contient la chaîne fournie,
    List<Provider> findByProviderNameContainingIgnoreCase(String providerName);

    //Recherche tous les providers dont le nom contient la chaîne fournie,
    boolean existsByProviderCode(String providerCode);
}