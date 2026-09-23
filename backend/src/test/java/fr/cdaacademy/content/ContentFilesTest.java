package fr.cdaacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Vérifie que tous les fichiers de contenu versionnés sont lisibles et cohérents. */
class ContentFilesTest {

    @Test
    void tousLesParcoursSontValides() {
        var bundles = new ContentLoader().loadAll(Path.of("../database/content"));
        assertThat(bundles).isNotEmpty();
        for (var bundle : bundles) {
            assertThat(bundle.chapters()).as(bundle.course().slug()).isNotEmpty();
            for (var chapter : bundle.chapters()) {
                assertThat(chapter.lessons()).as(chapter.slug()).isNotEmpty();
                for (var lesson : chapter.lessons()) {
                    long questions = lesson.questions() == null ? 0 : lesson.questions().size();
                    assertThat(questions).as("questions pendant la leçon " + lesson.slug()).isBetween(3L, 8L);
                    // Chaque petite leçon se termine par un QCM de plusieurs questions
                    var blocks = lesson.blocks() == null ? java.util.List.<com.fasterxml.jackson.databind.JsonNode>of()
                            : lesson.blocks();
                    assertThat(blocks.stream().filter(b -> b.path("type").asText().equals("quiz")).count())
                            .as("QCM de fin de leçon dans " + lesson.slug()).isEqualTo(1L);
                    assertThat(blocks.getLast().path("type").asText()).as("dernier bloc de " + lesson.slug())
                            .isEqualTo("quiz");
                }
            }
        }
    }
}
