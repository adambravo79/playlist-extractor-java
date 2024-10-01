package com.example.playlist_extractor.controller;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.ResourceId;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class PlaylistExtractorController {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistExtractorController.class);
    private static String API_KEY;
    private YouTube youtubeService;

    // Construtor que lê a API key do arquivo secrets.txt
    public PlaylistExtractorController() {
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        try {
            API_KEY = carregarApiKey("src/main/resources/secrets.txt"); // Lê a API key de um arquivo externo
            youtubeService = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, null)
                    .setApplicationName("Salvar URL de Playlist")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/salvar")
    public ResponseEntity<byte[]> salvar(@RequestParam("playlist_url") String playlistUrl,
            @RequestParam("nome_arquivo") String nomeArquivo,
            Model model) {
        logger.info("Processando a playlist...");
        String playlistId = playlistUrl.substring(playlistUrl.lastIndexOf("=") + 1);
        List<String> videoDetails = new ArrayList<>();
        List<String> urlsOnly = new ArrayList<>();

        // Definindo o diretório de saída
        Path outputDirectory = Paths.get("arquivos");
        try {
            if (!Files.exists(outputDirectory)) {
                Files.createDirectories(outputDirectory); // Cria o diretório caso não exista
            }

            // Caminhos dos arquivos gerados
            Path detalhesFilePath = outputDirectory.resolve(nomeArquivo + ".txt");
            Path urlsOnlyFilePath = outputDirectory.resolve(nomeArquivo + "-urlsonly.txt");

            // Obter os vídeos da playlist
            try {
                String nextPageToken = null;
                do {
                    YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                            .list(Collections.singletonList("snippet"))
                            .setFields("items/snippet/title,items/snippet/resourceId/videoId,nextPageToken")
                            .setKey(API_KEY)
                            .setPlaylistId(playlistId)
                            .setMaxResults(50L)
                            .setPageToken(nextPageToken);

                    PlaylistItemListResponse response = request.execute();
                    nextPageToken = response.getNextPageToken();

                    if (response.getItems() != null) {
                        for (PlaylistItem item : response.getItems()) {
                            ResourceId resourceId = item.getSnippet().getResourceId();
                            String videoId = resourceId.getVideoId();
                            String title = item.getSnippet().getTitle();

                            videoDetails
                                    .add(String.format("('%s', '%s', 'https://www.youtube.com/watch?v=%s')", title,
                                            videoId,
                                            videoId));
                            urlsOnly.add("https://www.youtube.com/watch?v=" + videoId);
                        }
                    }
                } while (nextPageToken != null);
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 403) {
                    logger.error("Limite de cota atingido, tente novamente mais tarde.");
                } else {
                    logger.error("Erro ao acessar API do YouTube: ", e);
                }
            }
            // Escrever o arquivo com as tuplas
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(detalhesFilePath.toFile()))) {
                for (String detail : videoDetails) {
                    writer.write(detail + "\n");
                }
            }

            // Escrever o arquivo apenas com URLs
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(urlsOnlyFilePath.toFile()))) {
                for (String url : urlsOnly) {
                    writer.write(url + "\n");
                }
            }

            // Criar o arquivo ZIP
            String zipFileName = nomeArquivo + ".zip";
            Path zipFilePath = outputDirectory.resolve(zipFileName);
            try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
                    ZipOutputStream zos = new ZipOutputStream(fos)) {

                // Adicionar o arquivo de detalhes ao ZIP
                adicionarArquivoAoZip(detalhesFilePath.toString(), zos);
                // Adicionar o arquivo apenas com URLs ao ZIP
                adicionarArquivoAoZip(urlsOnlyFilePath.toString(), zos);
            }

            // Ler o arquivo ZIP e retornar como resposta
            byte[] zipBytes = Files.readAllBytes(zipFilePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes);

        } catch (IOException e) {
            logger.error("Erro ao salvar a playlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Método para carregar a API key do arquivo secrets.txt
    private String carregarApiKey(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return br.readLine().trim(); // Lê a primeira linha e remove espaços em branco
        }
    }

    // Método auxiliar para adicionar um arquivo ao arquivo ZIP
    private void adicionarArquivoAoZip(String filePath, ZipOutputStream zos) throws IOException {
        File file = new File(filePath);
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        }
    }
}
