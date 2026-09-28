package com.nexus.repository;

import com.nexus.entity.TestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRepository
        extends JpaRepository<TestEntity, Long> {
}