package com.example.codeexplainer.repository;

import com.example.codeexplainer.model.SavedRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedRepoRepository extends JpaRepository<SavedRepo, Long> {
    boolean existsByUrl(String url);
}