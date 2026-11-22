package com.stech.schat.repository;

import com.stech.schat.model.TermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, UUID> {
}
