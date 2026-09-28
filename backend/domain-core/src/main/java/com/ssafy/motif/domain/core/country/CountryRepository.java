package com.ssafy.motif.domain.core.country;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CountryRepository extends JpaRepository<Country, Integer> {

    /** {@code uq_countries_code} 로 유일하다. 없으면 어드민 §5 {@code COUNTRY_NOT_FOUND}. */
    Optional<Country> findByCountryCode(String countryCode);
}
