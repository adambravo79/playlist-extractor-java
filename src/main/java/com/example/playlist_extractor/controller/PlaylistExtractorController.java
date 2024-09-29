package com.example.playlist_extractor.controller;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.ResourceId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class PlaylistExtractorController {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistExtractorController.class);
    private static String API_KEY = "AIzaSyCVOf4H7gxfX5rAHaBGKmXzd679QFGTFY4"; // Substitua pela sua chave de API
        private YouTube youtubeService;
    
        public PlaylistExtractorController() {
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            try {
                API_KEY = carregarApiKey("secrets.txt"); // Lê a API key de um arquivo externo
            youtubeService = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, null)
                    .setApplicationName("SeuProjeto")
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
    public String salvar(@RequestParam("playlist_url") String playlistUrl,
            @RequestParam("nome_arquivo") String nomeArquivo,
            Model model) {
        logger.info("Processando a playlist...");
        String playlistId = playlistUrl.substring(playlistUrl.lastIndexOf("=") + 1);
        List<String> videoDetails = new ArrayList<>();
        List<String> urlsOnly = new ArrayList<>();

        try {
            // Obter os vídeos da playlist
            YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                    .list(Collections.singletonList("snippet")) // Use uma lista para especificar a parte "snippet"
                    .setKey(API_KEY)
                    .setPlaylistId(playlistId)
                    .setMaxResults(50L);

            PlaylistItemListResponse response = request.execute();

            // Verifica se a resposta contém itens
            if (response.getItems() != null) {
                for (PlaylistItem item : response.getItems()) {
                    ResourceId resourceId = item.getSnippet().getResourceId();
                    String videoId = resourceId.getVideoId();
                    String title = item.getSnippet().getTitle();

                    // Logando o vídeo
                    logger.info("Adicionando vídeo: título='{}', id='{}'", title, videoId);

                    // Formatar a tupla com título, ID e URL completa
                    videoDetails.add(String.format("('%s', '%s', 'https://www.youtube.com/watch?v=%s')", title, videoId,
                            videoId));
                    // Adicionar apenas a URL
                    urlsOnly.add("https://www.youtube.com/watch?v=" + videoId);
                }
            }

            // Escrever o arquivo com as tuplas
            BufferedWriter writer = new BufferedWriter(new FileWriter(nomeArquivo + ".txt"));
            for (String detail : videoDetails) {
                writer.write(detail + "\n");
            }
            writer.close();

            // Escrever o arquivo apenas com as URLs
            BufferedWriter urlWriter = new BufferedWriter(new FileWriter(nomeArquivo + "-urlsonly.txt"));
            for (String url : urlsOnly) {
                urlWriter.write(url + "\n");
            }
            urlWriter.close();

            model.addAttribute("mensagem", "Arquivos salvos com sucesso!");
        } catch (IOException e) {
            logger.error("Erro ao salvar a playlist", e);
            model.addAttribute("mensagem", "Ocorreu um erro: " + e.getMessage());
        }

        return "index";
    }

    // Método para carregar a API key do arquivo secrets.txt
    private String carregarApiKey(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return br.readLine().trim(); // Lê a primeira linha e remove espaços em branco
        }
    }
}