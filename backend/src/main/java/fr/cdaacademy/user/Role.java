package fr.cdaacademy.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private RoleCode code;

    @Column(nullable = false, length = 60)
    private String label;

    protected Role() {
    }

    public Long getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
