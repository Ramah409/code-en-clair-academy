package fr.cdaacademy.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import fr.cdaacademy.user.dto.ChangePasswordRequest;
import fr.cdaacademy.user.dto.DeleteAccountRequest;
import fr.cdaacademy.user.dto.UpdateProfileRequest;
import fr.cdaacademy.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Mon compte")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Profil de l'utilisatrice connectée")
    public UserResponse me() {
        return userService.me(CurrentUser.id());
    }

    @PatchMapping
    @Operation(summary = "Modifier le nom affiché, l'objectif quotidien et le thème")
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(CurrentUser.id(), req);
    }

    @PutMapping("/password")
    @Operation(summary = "Changer son mot de passe")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(CurrentUser.id(), req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Supprimer son compte et toutes ses données (RGPD)")
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest req) {
        userService.deleteOwnAccount(CurrentUser.id(), req.password());
        return ResponseEntity.noContent().build();
    }
}
