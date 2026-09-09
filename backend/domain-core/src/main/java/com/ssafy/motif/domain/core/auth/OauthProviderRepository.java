package com.ssafy.motif.domain.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OauthProviderRepository extends JpaRepository<OauthProvider, Integer> {

    Optional<OauthProvider> findByProviderCode(String providerCode);
}
