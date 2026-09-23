package fr.cdaacademy.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /**
     * Recherche paramétrée (aucune concaténation SQL) sur le nom affiché ou l'e-mail. Une recherche vide
     * est passée en chaîne vide et non en null : PostgreSQL ne sait pas typer un paramètre null (bytea).
     */
    @Query("""
            select u from User u
            where :search = ''
               or lower(u.email) like lower(concat('%', :search, '%'))
               or lower(u.displayName) like lower(concat('%', :search, '%'))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);

    long countByRole_Code(RoleCode code);
}
