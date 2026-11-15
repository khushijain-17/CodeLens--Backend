package com.example.codeexplainer.service;

import com.example.codeexplainer.model.FileNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitService {

    private static final Pattern GITHUB_URL =
            Pattern.compile("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(\\.git)?/?$");
    private static final long MAX_AGE_MS = 60 * 60 * 1000L; // delete clones older than 1 hour
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".class", ".jar", ".zip", ".pdf", ".exe", ".dll");

    private final Path baseDir;

    public GitService(@Value("${codelens.repos-dir:repos}") String reposDir) throws IOException {
        this.baseDir = Files.createDirectories(Paths.get(reposDir)).toRealPath();
    }

    /** Accepts only https://github.com/<owner>/<repo> and returns the canonical .git URL. */
    public String normalizeGithubUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("No URL provided");
        }
        Matcher m = GITHUB_URL.matcher(url.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Only https://github.com/<owner>/<repo> URLs are supported");
        }
        return "https://github.com/" + m.group(1) + "/" + m.group(2) + ".git";
    }

    public File cloneRepo(String repoUrl) throws GitAPIException, IOException {
        String cloneUrl = normalizeGithubUrl(repoUrl);
        purgeOldClones();
        File dir = baseDir.resolve(UUID.randomUUID().toString()).toFile();
        try (Git git = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(dir)
                .setDepth(1) // shallow clone: we only need the latest files
                .call()) {
            return dir;
        }
    }

    public List<FileNode> getFileStructure(File dir) {
        List<FileNode> nodes = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return nodes;

        Arrays.sort(files, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase()));

        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            if (Files.isSymbolicLink(file.toPath())) continue;

            FileNode node = new FileNode();
            node.setName(file.getName());
            node.setPath(file.getAbsolutePath());
            node.setDirectory(file.isDirectory());
            if (file.isDirectory()) {
                node.setChildren(getFileStructure(file));
            }
            nodes.add(node);
        }
        return nodes;
    }

    /** Resolves a path and guarantees it is inside the cloned-repos folder. */
    public Path resolveInsideRepos(String requestedPath) throws IOException {
        Path real = Paths.get(requestedPath).toRealPath();   // resolves "..", symlinks
        if (!real.startsWith(baseDir)) {
            throw new AccessDeniedException("Path is outside the cloned repositories");
        }
        return real;
    }

    public String readFile(String requestedPath) throws IOException {
        Path file = resolveInsideRepos(requestedPath);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path is a directory, not a file");
        }
        if (isBinary(file.getFileName().toString())) {
            return "[Binary file — cannot display]";
        }
        try {
            return Files.readString(file);
        } catch (MalformedInputException e) {
            return "[Binary file — cannot display]";
        }
    }

    private boolean isBinary(String fileName) {
        String lower = fileName.toLowerCase();
        return BINARY_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private void purgeOldClones() {
        File[] dirs = baseDir.toFile().listFiles(File::isDirectory);
        if (dirs == null) return;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        for (File d : dirs) {
            if (d.lastModified() < cutoff) {
                FileSystemUtils.deleteRecursively(d);
            }
        }
    }
}