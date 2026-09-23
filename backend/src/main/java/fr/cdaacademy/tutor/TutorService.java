package fr.cdaacademy.tutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.common.TooManyRequestsException;
import fr.cdaacademy.tutor.CourseSearch.Hit;
import fr.cdaacademy.tutor.OllamaClient.Message;
import fr.cdaacademy.tutor.OllamaClient.OllamaUnavailableException;

/**
 * Assistant pédagogique : le modèle local Ollama répond s'il est disponible ; sinon, une réponse de
 * secours est construite à partir des cours (définitions, fiches, questions du jury), avec des liens
 * vers les leçons. Le contexte de la page (leçon, exercice) est transmis pour des réponses ciblées.
 */
@Service
public class TutorService {

    static final int MAX_QUESTION = 2000;
    static final int MAX_PER_HOUR = 40;
    static final Set<String> MODES = Set.of("GENERAL", "SQL", "MERISE", "JAVA", "SPRING", "ANGULAR", "SECURITE",
            "CORRECTION", "JURY", "PLANNING");

    static final String SYSTEM_PROMPT = """
            Tu es le tuteur de Code en Clair Academy, une plateforme qui prépare au titre professionnel
            Concepteur Développeur d'Applications (CDA). Les apprenantes peuvent être totalement débutantes.

            Règles :
            - Réponds en français très simple, avec des phrases courtes et une idée à la fois.
            - Explique chaque mot technique la première fois avec une comparaison de la vie courante.
            - Donne un petit exemple concret, puis propose une vérification ou une question pour s'entraîner.
            - Pour un exercice en cours, guide par étapes et par indices : ne donne pas la solution complète,
              sauf si l'apprenante dit avoir déjà essayé plusieurs fois et la demande explicitement.
            - Ne juge jamais une erreur : explique pourquoi c'est faux et ce qu'il faut revoir.
            - Si tu n'es pas sûr, dis-le. N'invente ni API ni commande.
            - Reste sur les sujets de la formation (développement, bases de données, sécurité, tests,
              déploiement, gestion de projet, préparation au jury).
            - Réponds en 200 mots maximum sauf si on te demande plus. Utilise le Markdown et des blocs de code courts.
            """;

    public record Link(String label, String url) {
    }

    public record MessageView(long id, String author, String content, String source, List<Link> links,
            Instant createdAt) {
    }

    public record ConversationSummary(long id, String title, String mode, Instant updatedAt) {
    }

    public record Conversation(long id, String title, String mode, List<MessageView> messages) {
    }

    public record AskRequest(Long conversationId, String question, String mode, String lessonSlug,
            String exerciseSlug) {
    }

    public record AskResult(long conversationId, MessageView answer, boolean aiAvailable) {
    }

    public record Status(boolean aiAvailable, String model) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OllamaClient ollama;
    private final CourseSearch search;
    private final Clock clock = Clock.systemUTC();
    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public TutorService(JdbcTemplate jdbc, ObjectMapper json, OllamaClient ollama, CourseSearch search) {
        this.jdbc = jdbc;
        this.json = json;
        this.ollama = ollama;
        this.search = search;
    }

    public Status status() {
        return new Status(ollama.available(), ollama.model());
    }

    // ------------------------------------------------------------------ conversations

    public List<ConversationSummary> conversations(long userId) {
        return jdbc.query("""
                select id, title, mode, updated_at from tutor_conversations where user_id = ?
                order by updated_at desc limit 30
                """, (rs, i) -> new ConversationSummary(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toInstant()), userId);
    }

    public Conversation conversation(long userId, long id) {
        List<Conversation> found = jdbc.query(
                "select id, title, mode from tutor_conversations where id = ? and user_id = ?",
                (rs, i) -> new Conversation(rs.getLong(1), rs.getString(2), rs.getString(3), messages(rs.getLong(1))),
                id, userId);
        if (found.isEmpty()) {
            throw new NotFoundException("Conversation introuvable.");
        }
        return found.getFirst();
    }

    @Transactional
    public void delete(long userId, long id) {
        if (jdbc.update("delete from tutor_conversations where id = ? and user_id = ?", id, userId) == 0) {
            throw new NotFoundException("Conversation introuvable.");
        }
    }

    // ------------------------------------------------------------------ question

    @Transactional
    public AskResult ask(long userId, AskRequest req) {
        String question = req.question() == null ? "" : req.question().strip();
        if (question.length() < 2) {
            throw new BusinessRuleException("Écris ta question avant de l'envoyer.");
        }
        if (question.length() > MAX_QUESTION) {
            throw new BusinessRuleException("Ta question dépasse " + MAX_QUESTION + " caractères : raccourcis-la.");
        }
        String mode = req.mode() != null && MODES.contains(req.mode()) ? req.mode() : "GENERAL";
        acquire(userId);

        long conversationId = req.conversationId() != null ? ownConversation(userId, req.conversationId())
                : jdbc.queryForObject("""
                        insert into tutor_conversations (user_id, mode, title) values (?, ?, ?) returning id
                        """, Long.class, userId, mode, title(question));
        List<MessageView> history = messages(conversationId);
        jdbc.update("insert into tutor_messages (conversation_id, author, content) values (?, 'USER', ?)",
                conversationId, question);

        String context = context(req.lessonSlug(), req.exerciseSlug());
        String answer;
        String source;
        List<Link> links;
        boolean ai = ollama.available();
        if (ai) {
            try {
                answer = ollama.chat(prompt(history, question, context));
                source = "OLLAMA";
                links = List.of();
            } catch (OllamaUnavailableException e) {
                ai = false;
                answer = null;
                source = null;
                links = null;
            }
        } else {
            answer = null;
            source = null;
            links = null;
        }
        if (!ai) {
            List<Hit> hits = search.search(question, 3);
            answer = fallback(question, hits);
            source = "COURS";
            links = hits.stream().map(h -> new Link(h.snippet().lessonTitle(), "/lecon/" + h.snippet().lessonSlug()))
                    .distinct().toList();
        }
        long id = jdbc.queryForObject("""
                insert into tutor_messages (conversation_id, author, content, source, links)
                values (?, 'ASSISTANT', ?, ?, ?::jsonb) returning id
                """, Long.class, conversationId, answer, source, write(links));
        jdbc.update("update tutor_conversations set updated_at = now() where id = ?", conversationId);
        return new AskResult(conversationId, new MessageView(id, "ASSISTANT", answer, source, links, Instant.now()),
                ai);
    }

    // ------------------------------------------------------------------ construction des réponses

    private List<Message> prompt(List<MessageView> history, String question, String context) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", SYSTEM_PROMPT + (context.isEmpty() ? "" : "\nContexte de la page :\n" + context)));
        for (MessageView m : history.subList(Math.max(0, history.size() - 8), history.size())) {
            messages.add(new Message("USER".equals(m.author()) ? "user" : "assistant", m.content()));
        }
        messages.add(new Message("user", question));
        return messages;
    }

    static String fallback(String question, List<Hit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("*L'assistant IA local n'est pas disponible pour le moment (Ollama n'est pas démarré). ");
        sb.append("Voici ce que disent les cours sur ta question.*\n\n");
        if (hits.isEmpty()) {
            sb.append("Je n'ai rien trouvé qui corresponde directement. Quelques pistes :\n\n");
            sb.append("- reformule avec le **mot technique** principal (par exemple « clé étrangère », « JWT », « composant ») ;\n");
            sb.append("- cherche la leçon correspondante dans **Parcours** ;\n");
            sb.append("- entraîne-toi sur le sujet dans **Quiz** : chaque correction explique la bonne réponse.");
            return sb.toString();
        }
        for (Hit h : hits) {
            var s = h.snippet();
            String label = switch (s.kind()) {
                case "DEFINITION" -> "**" + s.title() + "**";
                case "JURY" -> "**Question du jury : " + s.title() + "**";
                default -> "**" + s.title() + "**";
            };
            sb.append(label).append(" — leçon « ").append(s.lessonTitle()).append(" »\n\n");
            sb.append(truncate(s.text(), 700).strip()).append("\n\n");
        }
        sb.append("Ouvre la leçon indiquée pour revoir le passage avec ses exemples et ses exercices.");
        return sb.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, text.lastIndexOf(' ', max)) + " …";
    }

    private String context(String lessonSlug, String exerciseSlug) {
        StringBuilder sb = new StringBuilder();
        if (lessonSlug != null && !lessonSlug.isBlank()) {
            jdbc.query("select title, objective, memo from lessons where slug = ? and published", rs -> {
                sb.append("Leçon en cours : ").append(rs.getString(1)).append("\nObjectif : ").append(rs.getString(2));
                if (rs.getString(3) != null) {
                    sb.append("\nÀ retenir :\n").append(truncate(rs.getString(3), 1200));
                }
                sb.append('\n');
            }, lessonSlug);
        }
        if (exerciseSlug != null && !exerciseSlug.isBlank()) {
            jdbc.query("select title, statement from exercises where slug = ?", rs -> {
                sb.append("Exercice en cours : ").append(rs.getString(1)).append("\nÉnoncé :\n")
                        .append(truncate(rs.getString(2), 1500))
                        .append("\nNe donne pas la solution complète : guide avec des indices.\n");
            }, exerciseSlug);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ utilitaires

    private List<MessageView> messages(long conversationId) {
        return jdbc.query("""
                select id, author, content, source, links::text, created_at from tutor_messages
                where conversation_id = ? order by created_at, id
                """, (rs, i) -> new MessageView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                readLinks(rs.getString(5)), rs.getTimestamp(6).toInstant()), conversationId);
    }

    private long ownConversation(long userId, long id) {
        Integer n = jdbc.queryForObject("select count(*) from tutor_conversations where id = ? and user_id = ?",
                Integer.class, id, userId);
        if (n == null || n == 0) {
            throw new NotFoundException("Conversation introuvable.");
        }
        return id;
    }

    private static String title(String question) {
        String t = question.replaceAll("\\s+", " ");
        return t.length() <= 80 ? t : t.substring(0, 77) + "…";
    }

    private void acquire(long userId) {
        Instant now = clock.instant();
        Deque<Instant> recent = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                recent.pollFirst();
            }
            if (recent.size() >= MAX_PER_HOUR) {
                throw new TooManyRequestsException("Beaucoup de questions en une heure : fais une pause, "
                        + "ou relis la leçon en attendant.");
            }
            recent.addLast(now);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Link> readLinks(String raw) {
        try {
            return raw == null ? List.of() : json.readValue(raw, new TypeReference<List<Link>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
