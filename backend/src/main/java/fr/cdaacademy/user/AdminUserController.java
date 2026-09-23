package fr.cdaacademy.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.common.PageResponse;
import fr.cdaacademy.security.CurrentUser;
import fr.cdaacademy.user.dto.AdminUserResponse;
import fr.cdaacademy.user.dto.ChangeEnabledRequest;
import fr.cdaacademy.user.dto.ChangeRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Gestion des comptes, réservée au rôle ADMIN (contrôlé dans SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Administration — comptes")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lister et rechercher les comptes (nom ou e-mail)")
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return userService.search(search, page, size);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Changer le rôle d'un compte")
    public AdminUserResponse changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest req) {
        return userService.changeRole(CurrentUser.id(), id, req.role());
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "Activer ou désactiver un compte")
    public AdminUserResponse changeEnabled(@PathVariable Long id, @Valid @RequestBody ChangeEnabledRequest req) {
        return userService.changeEnabled(CurrentUser.id(), id, req.enabled());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un compte")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteByAdmin(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
