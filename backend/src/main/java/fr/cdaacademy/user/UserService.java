package fr.cdaacademy.user;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.common.PageResponse;
import fr.cdaacademy.common.UnauthorizedException;
import fr.cdaacademy.user.dto.AdminUserResponse;
import fr.cdaacademy.user.dto.ChangePasswordRequest;
import fr.cdaacademy.user.dto.UpdateProfileRequest;
import fr.cdaacademy.user.dto.UserResponse;

/** Gestion du profil de l'utilisatrice connectée et administration des comptes. */
@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserMapper.toResponse(find(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = find(userId);
        user.setDisplayName(req.displayName().trim());
        user.setDailyGoalMinutes(req.dailyGoalMinutes());
        user.setTheme(req.theme());
        return UserMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = find(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Le mot de passe actuel est incorrect.");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BusinessRuleException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    /** Droit à l'effacement (RGPD) : toutes les données liées sont supprimées en cascade par la base. */
    @Transactional
    public void deleteOwnAccount(Long userId, String password) {
        User user = find(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Mot de passe incorrect : le compte n'a pas été supprimé.");
        }
        ensureNotLastAdmin(user);
        users.delete(user);
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String search, int page, int size) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(users.search(term, pageable), UserMapper::toAdminResponse);
    }

    @Transactional
    public AdminUserResponse changeRole(Long adminId, Long targetId, RoleCode newRole) {
        User target = find(targetId);
        if (target.getId().equals(adminId) && newRole != RoleCode.ADMIN) {
            throw new BusinessRuleException("Tu ne peux pas retirer ton propre rôle d'administratrice.");
        }
        if (target.isAdmin() && newRole != RoleCode.ADMIN) {
            ensureNotLastAdmin(target);
        }
        target.setRole(roles.findByCode(newRole)
                .orElseThrow(() -> new NotFoundException("Rôle introuvable.")));
        return UserMapper.toAdminResponse(target);
    }

    @Transactional
    public AdminUserResponse changeEnabled(Long adminId, Long targetId, boolean enabled) {
        User target = find(targetId);
        if (target.getId().equals(adminId) && !enabled) {
            throw new BusinessRuleException("Tu ne peux pas désactiver ton propre compte.");
        }
        target.setEnabled(enabled);
        return UserMapper.toAdminResponse(target);
    }

    @Transactional
    public void deleteByAdmin(Long adminId, Long targetId) {
        if (targetId.equals(adminId)) {
            throw new BusinessRuleException(
                    "Pour supprimer ton propre compte, passe par la page « Mon compte ».");
        }
        User target = find(targetId);
        ensureNotLastAdmin(target);
        users.delete(target);
    }

    private void ensureNotLastAdmin(User user) {
        if (user.isAdmin() && users.countByRole_Code(RoleCode.ADMIN) <= 1) {
            throw new BusinessRuleException("Impossible : l'application doit conserver au moins une administratrice.");
        }
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Compte introuvable."));
    }
}
