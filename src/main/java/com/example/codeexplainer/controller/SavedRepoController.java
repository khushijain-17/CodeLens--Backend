package com.example.codeexplainer.controller;

import com.example.codeexplainer.model.SavedRepo;
import com.example.codeexplainer.repository.SavedRepoRepository;
import com.example.codeexplainer.service.GitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved")
public class SavedRepoController {

    @Autowired
    private SavedRepoRepository savedRepoRepository;

    @Autowired
    private GitService gitService;

    @GetMapping
    public List<SavedRepo> getAll() {
        return savedRepoRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body("No URL provided");
        }

        String normalized;
        try {
            normalized = gitService.normalizeGithubUrl(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        if (savedRepoRepository.existsByUrl(normalized)) {
            return ResponseEntity.ok("Already saved");
        }
        String name = normalized.replace("https://github.com/", "").replace(".git", "");
        SavedRepo saved = new SavedRepo(normalized, name);
        return ResponseEntity.ok(savedRepoRepository.save(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!savedRepoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        savedRepoRepository.deleteById(id);
        return ResponseEntity.ok("Deleted");
    }

    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAll() {
        savedRepoRepository.deleteAll();
        return ResponseEntity.ok("All deleted");
    }
}