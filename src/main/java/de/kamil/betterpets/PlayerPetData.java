package de.kamil.betterpets;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerPetData {
    private final List<OwnedPet> pets = new ArrayList<>();
    private UUID activePet;
    private boolean visible = true;

    public List<OwnedPet> pets() {
        return pets;
    }

    public Optional<OwnedPet> activePet() {
        if (activePet == null) {
            return Optional.empty();
        }
        return findPet(activePet);
    }

    public UUID activePetId() {
        return activePet;
    }

    public void setActivePet(final UUID activePet) {
        this.activePet = activePet;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
    }

    public Optional<OwnedPet> findPet(final UUID uuid) {
        return pets.stream().filter(pet -> pet.uuid().equals(uuid)).findFirst();
    }

    public boolean hasDefinition(final String definitionId) {
        return pets.stream().anyMatch(pet -> pet.definitionId().equals(definitionId));
    }

    public boolean removePet(final UUID uuid) {
        if (uuid.equals(activePet)) {
            activePet = null;
        }
        return pets.removeIf(pet -> pet.uuid().equals(uuid));
    }
}
