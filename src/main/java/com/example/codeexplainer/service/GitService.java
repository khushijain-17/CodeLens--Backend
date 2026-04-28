package com.example.codeexplainer.service;
import java.util.Scanner;
import org.eclipse.jgit.api.Git;
import java.io.File;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import com.example.codeexplainer.model.FileNode;

@Service
public class GitService {

    public File cloneRepo(String repoUrl) throws Exception {

        File dir = new File("repos/" + UUID.randomUUID());

        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(dir)
            .call();

        return dir;
    }

    public List<FileNode> getFileStructure(File dir) {

        List<FileNode> nodes = new ArrayList<>();

        File[] files = dir.listFiles();

        if (files == null) return nodes;

        for (File file : files) {

            if (file.getName().equals(".git")) continue;

            FileNode node = new FileNode();
            node.setName(file.getName());
            node.setDirectory(file.isDirectory());
            node.setPath(file.getAbsolutePath());

            if (file.isDirectory()) {
                node.setChildren(getFileStructure(file));
            }

            nodes.add(node);
        }

        return nodes;
    }
    public String getFileContent(String filePath) throws Exception {
        File file = new File(filePath);

        StringBuilder content = new StringBuilder();

        Scanner sc = new Scanner(file);
        while (sc.hasNextLine()) {
            content.append(sc.nextLine()).append("\n");
        }
        sc.close();

        return content.toString();
    }
}