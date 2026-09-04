package com.example.codeexplainer.controller;

import com.example.codeexplainer.service.AIService;
import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.codeexplainer.repository.SavedRepoRepository;
import com.example.codeexplainer.model.SavedRepo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/repo")
@CrossOrigin(origins = "*")
public class RepoController {

    @Autowired
    private AIService aiService;

    @Autowired
    private SavedRepoRepository savedRepoRepository;

    // ─────────────────────────────────────────
    // 1. ANALYZE
    // ─────────────────────────────────────────
    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> analyzeRepo(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("url");

        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body("No URL provided");
        }

        if (!repoUrl.endsWith(".git")) {
            repoUrl = repoUrl + ".git";
        }

        try {
            Path tempDir = Files.createTempDirectory("repo-");
            System.out.println("Cloning repo: " + repoUrl);
            System.out.println("Into directory: " + tempDir.toAbsolutePath());

            Git.cloneRepository()
               .setURI(repoUrl)
               .setDirectory(tempDir.toFile())
               .call();

            System.out.println("Clone successful!");

            String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");
            if (!savedRepoRepository.existsByUrl(repoUrl)) {
                savedRepoRepository.save(new SavedRepo(repoUrl, repoName));
            }

            List<Map<String, Object>> tree = buildTree(tempDir.toFile());
            return ResponseEntity.ok(tree);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to clone repo: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // 2. FILE
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
    // 3. EXPLAIN
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
    // 4. DEPENDENCY GRAPH
    // ─────────────────────────────────────────
    @GetMapping(value = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDependencyGraph(@RequestParam String repoPath) {
        try {
        	System.out.println("GRAPH repoPath = " + repoPath);
            Map<String, Object> graph = new HashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            java.io.File dir = new java.io.File(repoPath);
            if (!dir.exists()) {
                return ResponseEntity.badRequest().body("Repo path not found");
            }

            // Collect all code files
            List<java.io.File> files = new ArrayList<>();
            collectFiles(dir, files);

            // Build nodes
            Map<String, String> fileIdMap = new HashMap<>();
            for (java.io.File file : files) {
                String id = file.getName().replaceAll("[^a-zA-Z0-9]", "_");
                fileIdMap.put(file.getName(), id);

                Map<String, Object> node = new HashMap<>();
                node.put("id", id);
                node.put("name", file.getName());
                node.put("path", file.getAbsolutePath());
                node.put("type", getFileType(file.getName()));
                node.put("size", file.length());
                nodes.add(node);
            }

            // Build edges by scanning imports
            for (java.io.File file : files) {
                try {
                    String content = Files.readString(file.toPath());
                    String sourceId = fileIdMap.get(file.getName());

                    for (java.io.File otherFile : files) {
                        if (otherFile.equals(file)) continue;
                        String otherName = otherFile.getName()
                            .replaceAll("\\.(java|js|jsx|ts|tsx|py)$", "");

                        if (content.contains(otherName)) {
                            Map<String, Object> edge = new HashMap<>();
                            edge.put("source", sourceId);
                            edge.put("target", fileIdMap.get(otherFile.getName()));
                            edges.add(edge);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // ✅ Fixed: these lines were missing before
            graph.put("nodes", nodes);
            graph.put("edges", edges);
            graph.put("repoPath", repoPath);
            return ResponseEntity.ok(graph);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to build graph: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────
    private List<Map<String, Object>> buildTree(java.io.File dir) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        java.io.File[] files = dir.listFiles();

        if (files == null) return nodes;

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (java.io.File file : files) {
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

    private void collectFiles(java.io.File dir, List<java.io.File> files) {
        java.io.File[] children = dir.listFiles();
        if (children == null) return;
        for (java.io.File f : children) {
            if (f.getName().startsWith(".")) continue;
            if (f.getName().equals("target") || f.getName().equals("node_modules")) continue;
            if (f.isDirectory()) collectFiles(f, files);
            else if (isCodeFile(f.getName())) files.add(f);
        }
    }

    private boolean isCodeFile(String name) {
        return name.endsWith(".java") || name.endsWith(".js")  ||
               name.endsWith(".jsx")  || name.endsWith(".ts")  ||
               name.endsWith(".tsx")  || name.endsWith(".py")  ||
               name.endsWith(".go")   || name.endsWith(".cpp");
    }

    private String getFileType(String name) {
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".js") || name.endsWith(".jsx")) return "javascript";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".py")) return "python";
        return "other";
    }

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