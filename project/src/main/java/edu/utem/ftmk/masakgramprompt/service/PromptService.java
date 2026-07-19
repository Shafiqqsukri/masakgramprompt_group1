package edu.utem.ftmk.masakgramprompt.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PromptService {

    public String loadPromptFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    public String injectTranscript(String promptTemplate, String transcript) {
        return promptTemplate.replace("{{TRANSCRIPT}}", transcript);
    }
}