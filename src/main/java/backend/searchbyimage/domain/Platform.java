package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "platforms")
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @JsonIgnore
    @OneToMany(mappedBy = "platform", fetch = FetchType.LAZY)
    private List<Shop> shops = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "platform", fetch = FetchType.LAZY)
    private List<Category> categories = new ArrayList<>();

    public Platform() {}

    public Platform(String name) {
        this.name = name;
    }
}

