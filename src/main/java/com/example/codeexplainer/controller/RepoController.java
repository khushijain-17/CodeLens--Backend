package com.example.codeexplainer.controller;

import com.example.codeexplainer.model.SavedRepo;
import com.example.codeexplainer.repository.SavedRepoRepository;
import com.example.codeexplainer.service.AIService;
import com.example.codeexplainer.service.GitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.*;

@RestController
@RequestMapping("/api/repo")
public class RepoController {

    @Autowired
    private AIService aiService;

    @Autowired
    private SavedRepoRepository savedRepoRepository;

    @Autowired
    private GitService gitService;

    // ─────────────────────────────────────────
    // 1. ANALYZE
    // ─────────────────────────────────────────
    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> analyzeRepo(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("url");

        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body("No URL provided");
        }

        try {
            // Only https://github.com/<owner>/<repo> is accepted
            String cloneUrl = gitService.normalizeGithubUrl(repoUrl);
            // Shallow clone into the app's own repos/ folder
            File dir = gitService.cloneRepo(cloneUrl);

            String repoName = cloneUrl.substring(cloneUrl.lastIndexOf("/") + 1).replace(".git", "");
            if (!savedRepoRepository.existsByUrl(cloneUrl)) {
                savedRepoRepository.save(new SavedRepo(cloneUrl, repoName));
            }

            return ResponseEntity.ok(buildTree(dir));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to clone repo");
        }
    }

    // ─────────────────────────────────────────
    // 2. FILE
    // ─────────────────────────────────────────
    @GetMapping(value = "/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getFile(@RequestParam String path) {
        try {
            return ResponseEntity.ok(gitService.readFile(path));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (NoSuchFileException e) {
            return ResponseEntity.status(404).body("File not found");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading file");
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
            Map<String, Object> graph = new HashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            // Throws AccessDeniedException if repoPath is outside the repos folder
            File dir = gitService.resolveInsideRepos(repoPath).toFile();

            // Collect all code files
            List<File> files = new ArrayList<>();
            collectFiles(dir, files);

            // Build nodes
            Map<String, String> fileIdMap = new HashMap<>();
            for (File file : files) {
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
            for (File file : files) {
                try {
                    String content = Files.readString(file.toPath());
                    String sourceId = fileIdMap.get(file.getName());

                    for (File otherFile : files) {
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

            graph.put("nodes", nodes);
            graph.put("edges", edges);
            graph.put("repoPath", repoPath);
            return ResponseEntity.ok(graph);

        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (NoSuchFileException e) {
            return ResponseEntity.badRequest().body("Repo path not found");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to build graph");
        }
    }

    // ─────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────
    private List<Map<String, Object>> buildTree(File dir) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        File[] files = dir.listFiles();

        if (files == null) return nodes;

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            if (Files.isSymbolicLink(file.toPath())) continue;

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

    private void collectFiles(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.getName().startsWith(".")) continue;
            if (f.getName().equals("target") || f.getName().equals("node_modules")) continue;
            if (Files.isSymbolicLink(f.toPath())) continue;
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
}