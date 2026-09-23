package fr.cdaacademy.tutor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/tutor")
@Tag(name = "Assistant", description = "Assistant pédagogique local (Ollama) avec réponses de secours tirées des cours")
public class TutorController {

    private final TutorService tutor;

    public TutorController(TutorService tutor) {
        this.tutor = tutor;
    }

    @GetMapping("/status")
    @Operation(summary = "L'assistant IA local est-il disponible ?")
    public TutorService.Status status() {
        return tutor.status();
    }

    @GetMapping("/conversations")
    public List<TutorService.ConversationSummary> conversations() {
        return tutor.conversations(CurrentUser.id());
    }

    @GetMapping("/conversations/{id}")
    public TutorService.Conversation conversation(@PathVariable long id) {
        return tutor.conversation(CurrentUser.id(), id);
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        tutor.delete(CurrentUser.id(), id);
    }

    @PostMapping("/ask")
    @Operation(summary = "Poser une question (contexte facultatif : leçon ou exercice en cours)")
    public TutorService.AskResult ask(@RequestBody TutorService.AskRequest req) {
        return tutor.ask(CurrentUser.id(), req);
    }
}
