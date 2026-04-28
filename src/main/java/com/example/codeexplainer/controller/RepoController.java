package com.example.codeexplainer.controller;

import com.example.codeexplainer.service.AIService;
import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/repo")
@CrossOrigin(origins = "http://localhost:3000")
public class RepoController {

    @Autowired
    private AIService aiService;

    // ─────────────────────────────────────────
    // 1. ANALYZE — clone repo & return file tree
    // ─────────────────────────────────────────
    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> analyzeRepo(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("url");

        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body("No URL provided");
        }

        // Make sure URL ends with .git
        if (!repoUrl.endsWith(".git")) {
            repoUrl = repoUrl + ".git";
        }

        try {
            // Clone repo into a temp directory
            Path tempDir = Files.createTempDirectory("repo-");
            System.out.println("Cloning repo: " + repoUrl);
            System.out.println("Into directory: " + tempDir.toAbsolutePath());

            Git.cloneRepository()
               .setURI(repoUrl)
               .setDirectory(tempDir.toFile())
               .call();

            System.out.println("Clone successful!");

            // Build and return the file tree
            List<Map<String, Object>> tree = buildTree(tempDir.toFile());
            return ResponseEntity.ok(tree);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to clone repo: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // 2. FILE — read a single file's content
    // ─────────────────────────────────────────
    @GetMapping(value = "/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getFile(@RequestParam String path) {
        try {
            Path filePath = Paths.get(path);
            System.out.println("Requested file path: " + filePath.toAbsolutePath());

            if (!Files.exists(filePath)) {
                return ResponseEntity.badRequest()
                        .body("File not found: " + filePath.toAbsolutePath());
            }
            if (Files.isDirectory(filePath)) {
                return ResponseEntity.badRequest()
                        .body("Path is a directory, not a file: " + filePath.toAbsolutePath());
            }

            // Skip binary files (images, class files, etc.)
            String fileName = filePath.getFileName().toString();
            if (isBinaryFile(fileName)) {
                return ResponseEntity.ok("[Binary file — cannot display]");
            }

            String content = Files.readString(filePath);
            return ResponseEntity.ok(content);

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Error reading file: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // 3. EXPLAIN — send code to AI for explanation
    // ─────────────────────────────────────────
    @PostMapping(
        value = "/explain",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> explainCode(@RequestBody String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("No code provided");
        }
        try {
            String explanation = aiService.explainCode(code);
            return ResponseEntity.ok(explanation);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("AI error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // HELPER — recursively build file tree
    // ─────────────────────────────────────────
    private List<Map<String, Object>> buildTree(java.io.File dir) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        java.io.File[] files = dir.listFiles();

        if (files == null) return nodes;

        // Sort: folders first, then files, both alphabetically
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (java.io.File file : files) {
            // Skip hidden files and .git folder
            if (file.getName().startsWith(".")) continue;

            Map<String, Object> node = new HashMap<>();
            node.put("name", file.getName());
            node.put("path", file.getAbsolutePath());
            node.put("directory", file.isDirectory());

            if (file.isDirectory()) {
                node.put("children", buildTree(file));
            }

            nodes.add(node);
        }

        return nodes;
    }

    // ─────────────────────────────────────────
    // HELPER — detect binary files to skip them
    // ─────────────────────────────────────────
    private boolean isBinaryFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")  || lower.endsWith(".jpg")  ||
               lower.endsWith(".jpeg") || lower.endsWith(".gif")  ||
               lower.endsWith(".ico")  || lower.endsWith(".svg")  ||
               lower.endsWith(".class")|| lower.endsWith(".jar")  ||
               lower.endsWith(".zip")  || lower.endsWith(".pdf")  ||
               lower.endsWith(".exe")  || lower.endsWith(".dll");
    }
}