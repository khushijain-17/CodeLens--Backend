package com.example.codeexplainer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitServiceTest {

    @TempDir
    Path tempDir;

    private GitService gitService;
    private Path reposDir;

    @BeforeEach
    void setUp() throws Exception {
        reposDir = tempDir.resolve("repos");
        gitService = new GitService(reposDir.toString());
    }

    @Test
    void normalizesGithubUrls() {
        assertThat(gitService.normalizeGithubUrl("https://github.com/octocat/Hello-World"))
                .isEqualTo("https://github.com/octocat/Hello-World.git");
        assertThat(gitService.normalizeGithubUrl("https://github.com/octocat/Hello-World.git/"))
                .isEqualTo("https://github.com/octocat/Hello-World.git");
    }

    @Test
    void rejectsNonGithubUrls() {
        assertThatThrownBy(() -> gitService.normalizeGithubUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gitService.normalizeGithubUrl("https://evil.com/a/b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gitService.normalizeGithubUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsFilesInsideClonedRepos() throws Exception {
        Path dir = Files.createDirectories(reposDir.resolve("abc"));
        Path file = Files.writeString(dir.resolve("Hello.java"), "class Hello {}");
        assertThat(gitService.readFile(file.toString())).isEqualTo("class Hello {}");
    }

    @Test
    void blocksFilesOutsideReposDirectory() throws Exception {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "top secret");
        assertThatThrownBy(() -> gitService.readFile(secret.toString()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void blocksPathTraversal() throws Exception {
        Files.writeString(tempDir.resolve("secret.txt"), "top secret");
        Files.createDirectories(reposDir.resolve("abc"));
        String sneaky = reposDir.resolve("abc") + "/../../secret.txt";
        assertThatThrownBy(() -> gitService.readFile(sneaky))
                .isInstanceOf(AccessDeniedException.class);
    }
}